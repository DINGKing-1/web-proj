package com.secondhand.mall.service;

import com.secondhand.mall.entity.Address;
import com.secondhand.mall.repository.AddressRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public List<Address> getAddresses(String username) {
        return addressRepository.findByUsernameOrderByIsDefaultDescCreatedAtDesc(username);
    }

    public Address addAddress(Address address) {
        if (address.getIsDefault()) {
            clearDefault(address.getUsername());
        }
        return addressRepository.save(address);
    }

    public Address updateAddress(Long id, Address updates, String currentUsername) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("地址不存在"));

        if (!address.getUsername().equals(currentUsername)) {
            throw new RuntimeException("无权修改此地址");
        }

        if (updates.getReceiverName() != null) address.setReceiverName(updates.getReceiverName());
        if (updates.getPhone() != null) address.setPhone(updates.getPhone());
        if (updates.getProvince() != null) address.setProvince(updates.getProvince());
        if (updates.getCity() != null) address.setCity(updates.getCity());
        if (updates.getDistrict() != null) address.setDistrict(updates.getDistrict());
        if (updates.getDetail() != null) address.setDetail(updates.getDetail());

        if (Boolean.TRUE.equals(updates.getIsDefault())) {
            clearDefault(address.getUsername());
            address.setIsDefault(true);
        }

        return addressRepository.save(address);
    }

    public void deleteAddress(Long id, String currentUsername) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("地址不存在"));

        if (!address.getUsername().equals(currentUsername)) {
            throw new RuntimeException("无权删除此地址");
        }

        addressRepository.delete(address);
    }

    private void clearDefault(String username) {
        List<Address> addresses = addressRepository.findByUsernameOrderByIsDefaultDescCreatedAtDesc(username);
        for (Address a : addresses) {
            if (a.getIsDefault()) {
                a.setIsDefault(false);
                addressRepository.save(a);
            }
        }
    }
}
