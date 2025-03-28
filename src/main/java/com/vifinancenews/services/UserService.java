package com.vifinancenews.services;

import com.vifinancenews.models.Identifier;
import com.vifinancenews.daos.AccountDAO;
import com.vifinancenews.daos.IdentifierDAO;
import com.vifinancenews.models.Account;
import com.vifinancenews.utilities.EmailUtility;
import com.vifinancenews.utilities.IDHash;
import com.vifinancenews.utilities.PasswordHash;
import com.vifinancenews.utilities.OTPGenerator;
import com.vifinancenews.utilities.RedisOTPService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

public class UserService {

    public boolean registerUser(String email, String password, String userName, String avatarLink, String bio) throws SQLException {
        String passwordHash = PasswordHash.hashPassword(password);
    
        // Create Identifier entry
        Identifier newIdentifier = IdentifierDAO.insertIdentifier(email, passwordHash);
        if (newIdentifier == null) return false;
    
        // Create Account entry with hashed ID
        Account newAccount = AccountDAO.insertAccount(newIdentifier.getId(), userName, avatarLink, bio);
        return newAccount != null;
    }

    public boolean deleteUserByEmail(String email) throws SQLException {
        Identifier user = IdentifierDAO.getIdentifierByEmail(email);
        if (user == null) return false;

        boolean accountDeleted = AccountDAO.deleteAccountByUserId(user.getId());
        boolean identifierDeleted = IdentifierDAO.deleteIdentifierByEmail(email);
        return accountDeleted && identifierDeleted;
    }

    public boolean deleteUserById(UUID userId) throws SQLException {
        boolean accountDeleted = AccountDAO.deleteAccountByUserId(userId);
        boolean identifierDeleted = IdentifierDAO.deleteIdentifierByUserId(userId);
        return accountDeleted && identifierDeleted;
    }

    public boolean verifyPassword(String email, String password) throws SQLException {
        Identifier user = fetchUser(email);
        if (user == null) {
            System.out.println("User not found: " + email);
            return false;
        }

        if (isAccountLocked(user)) {
            return false;
        }

        boolean passwordMatches = PasswordHash.verifyPassword(password, user.getPasswordHash());
        if (!passwordMatches) {
            handleFailedLoginAttempt(user);
            return false;
        }

        sendOTP(email); // Send OTP after successful password verification
        return true;
    }

    public String login(String email, String enteredOTP) throws SQLException {
        if (!verifyOTP(email, enteredOTP)) {
            return null; // OTP incorrect or expired
        }
        
        Identifier user = fetchUser(email);
        return (user != null) ? user.getId().toString() : null; // Return user ID if OTP is valid
    }
    

    private Identifier fetchUser(String email) throws SQLException {
        return IdentifierDAO.getIdentifierByEmail(email);
    }

    private boolean isAccountLocked(Identifier user) {
        if (user.isLocked()) {
            System.out.println("Account locked until: " + user.getLockoutUntil());
            return true;
        }
        return false;
    }

    private void handleFailedLoginAttempt(Identifier user) throws SQLException {
        int newFailedAttempts = user.getFailedAttempts() + 1;
        LocalDateTime lockoutUntil = newFailedAttempts >= 5 ? LocalDateTime.now().plusMinutes(15) : null;
        IdentifierDAO.updateFailedAttempts(user.getEmail(), newFailedAttempts, lockoutUntil);
    }

    private void sendOTP(String email) {
        String otp = OTPGenerator.generateOTP();
        RedisOTPService.storeOTP(email, otp);
        System.out.println("Generated OTP: " + otp); // Log OTP for debugging
        try {
            EmailUtility.sendOTP(email, otp);
            System.out.println("OTP sent to " + email);
        } catch (Exception e) {
            System.out.println("Error sending OTP: " + e.getMessage());
        }
    }

    private boolean verifyOTP(String email, String enteredOTP) throws SQLException {
        if (!RedisOTPService.verifyOTP(email, enteredOTP)) {
            System.out.println("Invalid or expired OTP.");
            return false;
        }

        IdentifierDAO.resetFailedAttempts(email);
        return true;
    }

    public Account getUserProfile(UUID userId) throws SQLException {
        return AccountDAO.getAccountByUserId(userId);
    }
    

    public boolean updateUserProfile(UUID userId, String userName, String avatarLink, String bio) throws SQLException {
        return AccountDAO.updateAccount(userId, userName, avatarLink, bio);
    }
    

}
