package com.secondhand.mall.service;

import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.Address;
import com.secondhand.mall.entity.Offer;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.repository.AddressRepository;
import com.secondhand.mall.repository.OfferRepository;
import com.secondhand.mall.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 议价：买家出价 → 卖家接受（按出价生成待付款订单）/ 拒绝。
 */
@Service
public class OfferService {

    /**
     * 商品不可售导致的自动拒绝：标记 rejected 后抛出，
     * 由 {@code @Transactional(noRollbackFor=...)} 保证 rejected 落库且错误仍上抛。
     */
    public static class OfferAutoRejectedException extends RuntimeException {
        public OfferAutoRejectedException(String message) {
            super(message);
        }
    }

    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;

    public OfferService(OfferRepository offerRepository, ProductRepository productRepository,
                        AddressRepository addressRepository, OrderService orderService,
                        NotificationService notificationService) {
        this.offerRepository = offerRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.orderService = orderService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Offer createOffer(String buyerUsername, Long productId, BigDecimal offerPrice, String message) {
        if (buyerUsername == null || buyerUsername.isBlank()) {
            throw new RuntimeException("请先登录");
        }
        if (offerPrice == null || offerPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("出价必须大于 0");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new RuntimeException("该商品当前不可议价");
        }
        if (buyerUsername.equals(product.getSellerUsername())) {
            throw new RuntimeException("不能对自己商品出价");
        }
        BigDecimal price = product.getSecondHandPrice();
        if (offerPrice.compareTo(price) >= 0) {
            throw new RuntimeException("出价需低于当前售价 ¥" + price.toPlainString() + "，可直接购买");
        }
        // 同一商品同一买家仅允许一条待处理出价
        List<Offer> pending = offerRepository.findByProductIdAndBuyerUsernameAndStatus(
                productId, buyerUsername, "pending");
        if (!pending.isEmpty()) {
            throw new RuntimeException("你已有待处理的出价，请等待卖家回应");
        }

        Offer offer = new Offer();
        offer.setProductId(productId);
        offer.setProductName(product.getName());
        offer.setImageUrl(firstImage(product.getImageUrl()));
        offer.setBuyerUsername(buyerUsername);
        offer.setSellerUsername(product.getSellerUsername());
        offer.setOfferPrice(offerPrice.setScale(2, java.math.RoundingMode.HALF_UP));
        offer.setMessage(message != null && message.length() > 200 ? message.substring(0, 200) : message);
        offer.setStatus("pending");

        Offer saved = offerRepository.save(offer);
        notificationService.push(product.getSellerUsername(), "offer", "收到买家出价",
                "「" + product.getName() + "」买家出价 ¥" + saved.getOfferPrice().toPlainString()
                        + (message != null && !message.isBlank() ? "，留言：" + message : ""),
                "offer", String.valueOf(saved.getId()));
        return saved;
    }

    /**
     * 卖家接受出价：按出价金额生成待付款订单（占用库存），出价标记 accepted。
     * 商品不可售时自动拒绝：rejected 提交后仍抛错给调用方。
     */
    @Transactional(noRollbackFor = OfferAutoRejectedException.class)
    public Offer acceptOffer(Long offerId, String sellerUsername) {
        Offer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new RuntimeException("出价不存在"));
        if (!sellerUsername.equals(offer.getSellerUsername())) {
            throw new RuntimeException("只有卖家可以处理此出价");
        }
        if (!"pending".equals(offer.getStatus())) {
            throw new RuntimeException("该出价已处理");
        }

        Product product = productRepository.findById(offer.getProductId())
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        if (!ProductStatus.ACTIVE.equals(product.getStatus()) || product.getQuantity() == null || product.getQuantity() < 1) {
            offer.setStatus("rejected");
            offer.setRespondedAt(LocalDateTime.now());
            offerRepository.save(offer);
            throw new OfferAutoRejectedException("商品已下架或售罄，出价已自动拒绝");
        }

        // 用买家默认/最新地址生成待付款订单
        List<Address> addresses = addressRepository.findByUsernameOrderByIsDefaultDescCreatedAtDesc(offer.getBuyerUsername());
        if (addresses.isEmpty()) {
            throw new RuntimeException("买家尚未添加收货地址，请提醒买家完善地址后再接受出价");
        }
        Address addr = addresses.get(0);

        var order = orderService.createNegotiatedOrder(
                offer.getBuyerUsername(),
                offer.getProductId(),
                offer.getOfferPrice(),
                product.getName(),
                product.getCategory(),
                product.getImageUrl(),
                addr.getReceiverName(),
                addr.getPhone(),
                addr.getProvince() + addr.getCity() + addr.getDistrict() + addr.getDetail(),
                "balance",
                1
        );

        offer.setStatus("accepted");
        offer.setOrderId(order.getId());
        offer.setRespondedAt(LocalDateTime.now());
        Offer saved = offerRepository.save(offer);

        notificationService.push(offer.getBuyerUsername(), "offer", "出价已被接受",
                "「" + offer.getProductName() + "」出价 ¥" + offer.getOfferPrice().toPlainString()
                        + " 已被卖家接受，请尽快付款", "order", String.valueOf(order.getId()));
        return saved;
    }

    @Transactional
    public Offer rejectOffer(Long offerId, String sellerUsername) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new RuntimeException("出价不存在"));
        if (!sellerUsername.equals(offer.getSellerUsername())) {
            throw new RuntimeException("只有卖家可以处理此出价");
        }
        if (!"pending".equals(offer.getStatus())) {
            throw new RuntimeException("该出价已处理");
        }
        offer.setStatus("rejected");
        offer.setRespondedAt(LocalDateTime.now());
        Offer saved = offerRepository.save(offer);
        notificationService.push(offer.getBuyerUsername(), "offer", "出价被拒绝",
                "「" + offer.getProductName() + "」出价 ¥" + offer.getOfferPrice().toPlainString()
                        + " 被卖家拒绝", "offer", String.valueOf(offer.getId()));
        return saved;
    }

    @Transactional
    public Offer cancelOffer(Long offerId, String buyerUsername) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new RuntimeException("出价不存在"));
        if (!buyerUsername.equals(offer.getBuyerUsername())) {
            throw new RuntimeException("只有买家可以撤销出价");
        }
        if (!"pending".equals(offer.getStatus())) {
            throw new RuntimeException("该出价已处理，无法撤销");
        }
        offer.setStatus("cancelled");
        offer.setRespondedAt(LocalDateTime.now());
        return offerRepository.save(offer);
    }

    public List<Offer> listBuyerOffers(String buyerUsername) {
        return offerRepository.findByBuyerUsernameOrderByCreatedAtDesc(buyerUsername);
    }

    public List<Offer> listSellerOffers(String sellerUsername) {
        return offerRepository.findBySellerUsernameOrderByCreatedAtDesc(sellerUsername);
    }

    private static String firstImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return "";
        String first = imageUrl.split(",")[0].trim();
        return first.length() > 255 ? first.substring(0, 255) : first;
    }
}
