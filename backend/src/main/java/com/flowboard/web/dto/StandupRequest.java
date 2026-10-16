package com.flowboard.web.dto;

import jakarta.validation.constraints.Size;

/** @param timeZone optional IANA zone such as "Europe/London", used to decide what "today" is */
public record StandupRequest(@Size(max = 64) String timeZone) {
}
