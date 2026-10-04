package com.secondhand.mall.service;

import com.secondhand.mall.entity.QuickReply;
import com.secondhand.mall.repository.QuickReplyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuickReplyService {

    private final QuickReplyRepository quickReplyRepository;

    public QuickReplyService(QuickReplyRepository quickReplyRepository) {
        this.quickReplyRepository = quickReplyRepository;
    }

    /** 首次访问时写入默认话术 */
    @Transactional
    public void seedDefaultsIfEmpty() {
        if (quickReplyRepository.count() > 0) return;
        String[][] defaults = {
            {"交易", "如何下单", "选好商品后点击「立即购买」或加入购物车，填写收货地址并使用余额支付即可完成下单。"},
            {"交易", "支付方式", "目前支持余额支付。可在个人中心点击「充值」为账户余额充值。"},
            {"售后", "如何退款", "未发货订单可直接取消，余额自动退还。已发货订单请联系客服协商处理。"},
            {"售后", "收货确认", "收到商品后请在订单详情点击「确认收货」，然后评价并完成订单。"},
            {"账号", "忘记密码", "在登录页点击「忘记密码」，输入用户名和注册邮箱即可重置密码。"},
            {"账号", "如何发布商品", "登录后点击「发布」，填写商品信息并上传图片即可上架。"},
            {"其他", "转人工", "您好，客服在线中，请描述您遇到的问题，我们会尽快为您处理。"},
            {"其他", "结束语", "感谢您的咨询，祝您购物愉快！如有其他问题欢迎随时联系。"}
        };
        int order = 0;
        for (String[] row : defaults) {
            QuickReply r = new QuickReply();
            r.setCategory(row[0]);
            r.setTitle(row[1]);
            r.setContent(row[2]);
            r.setSortOrder(order++);
            quickReplyRepository.save(r);
        }
    }

    public List<QuickReply> list() {
        seedDefaultsIfEmpty();
        return quickReplyRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public QuickReply get(Long id) {
        return quickReplyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("话术不存在"));
    }

    /** 按标题精确匹配话术（用户点常见问题时用） */
    public QuickReply findByTitle(String title) {
        if (title == null) return null;
        String key = title.trim();
        return quickReplyRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(r -> key.equals(r.getTitle()))
                .findFirst()
                .orElse(null);
    }

    public QuickReply create(QuickReply reply) {
        return quickReplyRepository.save(reply);
    }

    @Transactional
    public QuickReply update(Long id, QuickReply patch) {
        QuickReply r = quickReplyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("话术不存在"));
        if (patch.getCategory() != null) r.setCategory(patch.getCategory());
        if (patch.getTitle() != null) r.setTitle(patch.getTitle());
        if (patch.getContent() != null) r.setContent(patch.getContent());
        if (patch.getSortOrder() != null) r.setSortOrder(patch.getSortOrder());
        return quickReplyRepository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        quickReplyRepository.deleteById(id);
    }
}
