package com.example.application.auth

interface EmailSender {
    fun sendAuthCode(email: String, code: String)
}
