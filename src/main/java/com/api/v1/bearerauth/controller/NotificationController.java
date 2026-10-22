package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.common.response.PaginationResponse;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.response.NotificationDto;
import com.api.v1.bearerauth.model.response.UnreadCountDto;
import com.api.v1.bearerauth.service.NotificationService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// My notifications: any logged-in user, and only their own.
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    // filter: all (default) | unread; newest activity first
    @GetMapping
    public ResponseEntity<PaginationResponse<List<NotificationDto>>> list(@RequestParam(defaultValue = "all") String filter,
                                                                          @RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        boolean unreadOnly;
        if ("unread".equalsIgnoreCase(filter)) {
            unreadOnly = true;
        } else if ("all".equalsIgnoreCase(filter)) {
            unreadOnly = false;
        } else {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, notificationService.list(unreadOnly, page, size))
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<SuccessResponse<UnreadCountDto>> unreadCount() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, new UnreadCountDto(notificationService.unreadCount()))
        );
    }

    @PatchMapping("/read-all")
    public ResponseEntity<SuccessResponse<Void>> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<SuccessResponse<NotificationDto>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, notificationService.markRead(id))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<Void>> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }
}
