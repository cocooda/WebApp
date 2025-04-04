package com.vifinancenews.controllers;

import io.javalin.http.Handler;
import com.vifinancenews.services.UserService;
import com.vifinancenews.models.Account;
import java.util.Map;
import java.util.UUID;

public class UserController {
    private static final UserService userService = new UserService();

    public static Handler getUserProfile = ctx -> {
        String userId = ctx.sessionAttribute("userId");
        if (userId == null) {
            ctx.status(401).result("Unauthorized");
            return;
        }

        UUID uuid = UUID.fromString(userId);
        Account account = userService.getUserProfile(uuid);
        if (account == null) {
            ctx.status(404).result("User not found");
        } else {
            ctx.json(account);
        }
    };

    public static Handler updateUserProfile = ctx -> {
    String userId = ctx.sessionAttribute("userId");
    if (userId == null) {
        ctx.status(401).result("Unauthorized");
        return;
    }

    UUID uuid = UUID.fromString(userId);

    // Parse JSON request into a Map
    Map<String, String> requestData = ctx.bodyAsClass(Map.class);

    String userName = requestData.get("userName");
    String avatarLink = requestData.get("avatarLink"); // Can be null
    String bio = requestData.get("bio"); // Can be null

    // Validate required field
    if (userName == null || userName.isEmpty()) {
        ctx.status(400).result("Username cannot be empty");
        return;
    }

    boolean success = userService.updateUserProfile(uuid, userName, avatarLink, bio);

    if (success) {
        ctx.status(200).result("Profile updated successfully");
    } else {
        ctx.status(400).result("Failed to update profile");
    }
};
    

public static Handler deleteUser = ctx -> {
    String userId = ctx.sessionAttribute("userId");
    if (userId == null) {
        ctx.status(401).result("Unauthorized");
        return;
    }

    UUID uuid = UUID.fromString(userId);
    boolean softDeleted = userService.softDeleteUserById(uuid);  // Call soft delete method

    if (softDeleted) {
        ctx.sessionAttribute("userId", null);
        // Update the response to include the 30-day deactivation period
        ctx.status(200).result("Your account has been deactivated for 30 days before permanent deletion. You can restore it during this period.");
    } else {
        ctx.status(400).result("Failed to soft delete user.");
    }
};


}
