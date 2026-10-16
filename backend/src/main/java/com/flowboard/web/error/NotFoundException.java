package com.flowboard.web.error;

/**
 * The resource does not exist, or it exists but belongs to someone else. Both cases are reported as 404 so the
 * API never confirms that another user's resource exists.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
