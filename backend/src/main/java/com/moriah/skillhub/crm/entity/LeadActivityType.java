package com.moriah.skillhub.crm.entity;

/** {@code lead_activities.activity_type} is free text in the schema (no CHECK constraint, same
 * treatment as {@code pip_records.trigger_reason}) but every value {@code LeadService} itself
 * ever writes is one of these. {@code WHATSAPP_INBOUND} is written only by the inbound webhook
 * path, never by an agent through {@code POST /leads/{id}/activities}. */
public enum LeadActivityType {
    CALL,
    EMAIL,
    WHATSAPP,
    MEETING,
    NOTE,
    STATUS_CHANGE,
    WHATSAPP_INBOUND
}
