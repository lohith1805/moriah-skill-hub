package com.moriah.skillhub.common.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves the caller's {@code uuid} from the {@code SecurityContext} with no DB round trip —
 * {@link AuthenticatedPrincipal}'s own Javadoc anticipates exactly this. Same {@code instanceof}
 * guard as {@link CurrentUser}, for the same reason (an anonymous request's principal is the bare
 * string {@code "anonymousUser"}, not {@code null}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression =
        "#this instanceof T(com.moriah.skillhub.common.security.AuthenticatedPrincipal) ? uuid : null")
public @interface CurrentUserUuid {
}
