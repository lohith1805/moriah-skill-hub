package com.moriah.skillhub.common.security;

import java.util.List;

/**
 * The {@code Authentication} principal {@code JwtAuthFilter} sets after validating a token.
 * {@code userId} (never {@code uuid}) is what the rest of the application uses internally —
 * {@code uuid} is kept only for cases that need the public identifier without a DB round trip.
 */
public record AuthenticatedPrincipal(Long userId, String uuid, List<String> roles) {
}
