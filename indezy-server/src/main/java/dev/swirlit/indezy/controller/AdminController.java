package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.AdminDtos;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Usage, accounts and features, for the administrators named in INDEZY_ADMIN_EMAILS")
public class AdminController {

    private final AdminService admin;

    @Operation(summary = "Accounts, sign-ups and the workspaces using each feature")
    @GetMapping("/overview")
    public AdminDtos.Overview overview() {
        admin.requireAdmin(SecurityUtils.getCurrentUserId());
        return admin.overview();
    }

    @Operation(summary = "Accounts whose email or name holds the text, newest first, 20 a page")
    @GetMapping("/accounts")
    public AdminDtos.AccountPage accounts(@RequestParam(required = false) String query, @RequestParam(defaultValue = "0") int page) {
        admin.requireAdmin(SecurityUtils.getCurrentUserId());
        return admin.accounts(query, page);
    }

    @Operation(summary = "Disable an account: it can no longer sign in and its tokens stop working",
        description = "409 for an administrator's account")
    @PostMapping("/accounts/{id}/disable")
    public AdminDtos.Account disable(@PathVariable Long id) {
        Long adminId = SecurityUtils.getCurrentUserId();
        admin.requireAdmin(adminId);
        return admin.setDisabled(adminId, id, true);
    }

    @Operation(summary = "Enable a disabled account again")
    @PostMapping("/accounts/{id}/enable")
    public AdminDtos.Account enable(@PathVariable Long id) {
        Long adminId = SecurityUtils.getCurrentUserId();
        admin.requireAdmin(adminId);
        return admin.setDisabled(adminId, id, false);
    }

    @Operation(summary = "Every feature that can be turned off, and whether it is on")
    @GetMapping("/features")
    public List<AdminDtos.FeatureState> features() {
        admin.requireAdmin(SecurityUtils.getCurrentUserId());
        return admin.features();
    }

    @Operation(summary = "Turn a feature on or off for everyone, without redeploying")
    @PutMapping("/features/{flag}")
    public List<AdminDtos.FeatureState> setFeature(@PathVariable FeatureFlag flag, @RequestBody AdminDtos.FeatureUpdate update) {
        Long adminId = SecurityUtils.getCurrentUserId();
        admin.requireAdmin(adminId);
        return admin.setFeature(adminId, flag, update.enabled());
    }
}
