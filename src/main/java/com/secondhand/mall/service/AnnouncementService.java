package com.secondhand.mall.service;

import com.secondhand.mall.aspect.Audited;
import com.secondhand.mall.entity.Announcement;
import com.secondhand.mall.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    public List<Announcement> listEnabled() {
        return announcementRepository.findByEnabledTrueOrderBySortOrderAscCreatedAtDesc();
    }

    public List<Announcement> listAll() {
        return announcementRepository.findAllByOrderBySortOrderAscCreatedAtDesc();
    }

    @Audited(action = "新建公告", targetType = "announcement")
    @Transactional
    public Announcement create(Announcement a) {
        if (a.getTitle() == null || a.getTitle().isBlank()) {
            throw new RuntimeException("标题不能为空");
        }
        return announcementRepository.save(a);
    }

    @Audited(action = "修改公告", targetType = "announcement")
    @Transactional
    public Announcement update(Long id, Announcement patch) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("公告不存在"));
        if (patch.getTitle() != null && !patch.getTitle().isBlank()) a.setTitle(patch.getTitle());
        if (patch.getContent() != null) a.setContent(patch.getContent());
        if (patch.getType() != null) a.setType(patch.getType());
        if (patch.getImageUrl() != null) a.setImageUrl(patch.getImageUrl());
        if (patch.getLinkUrl() != null) a.setLinkUrl(patch.getLinkUrl());
        if (patch.getSortOrder() != null) a.setSortOrder(patch.getSortOrder());
        if (patch.getEnabled() != null) a.setEnabled(patch.getEnabled());
        return announcementRepository.save(a);
    }

    @Audited(action = "删除公告", targetType = "announcement")
    @Transactional
    public void delete(Long id) {
        announcementRepository.deleteById(id);
    }
}
