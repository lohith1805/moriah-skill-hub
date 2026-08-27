package com.moriah.skillhub.client.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code user} is a real, nullable {@code @OneToOne User} — the shared-kernel exception every
 * other feature's own {@code User} association already gets, made optional here because
 * build-plan.md feature 21 is explicit that a client company can exist with no portal login at
 * all ("CLIENT users are provisioned by ADMIN — a client cannot self-register";
 * {@code ClientService#create} only links one when the request opts in via {@code
 * provisionPortalLogin}). {@code uq_clients_user} in V15 backs the one-to-one at the DB level
 * too — a {@code User} can be linked to at most one {@code Client} row.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client extends BaseEntity {

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "contact_person", nullable = false, length = 150)
    private String contactPerson;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String industry;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status = ClientStatus.ACTIVE;
}
