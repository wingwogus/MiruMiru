package com.example.application.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

class CustomUserDetails(
    val memberDto: MemberDto,
    private val attributes: Map<String, Any>? = null
) : UserDetails, OAuth2User {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_USER"))
    }

    override fun getPassword(): String? = null
    override fun getUsername(): String = memberDto.email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
    override fun getAttributes(): Map<String, Any>? = this.attributes
    override fun getName(): String = memberDto.provider
}