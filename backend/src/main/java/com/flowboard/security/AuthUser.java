package com.flowboard.security;

/**
 * The signed-in user, as stored in the SecurityContext. Controllers receive it with @AuthenticationPrincipal
 * and pass its id to the services - the user id never comes from the request body or the URL.
 */
public record AuthUser(Long id, String username) {
}
