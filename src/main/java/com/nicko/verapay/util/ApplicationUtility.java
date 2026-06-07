package com.nicko.verapay.util;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class ApplicationUtility {

    public static String getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {
            return ApplicationConstants.SYSTEM;
        }
        Object principal = authentication.getPrincipal();
        String username;
        if (principal instanceof User jobPortalUser) {
            username = jobPortalUser.getEmail();
        } else {
            username = principal.toString(); // fallback
        }
        return username;
    }

}
