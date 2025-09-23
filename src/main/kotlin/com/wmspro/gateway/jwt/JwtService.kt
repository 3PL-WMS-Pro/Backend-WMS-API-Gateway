package com.wmspro.gateway.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtService {
    
    companion object {
        private const val SECRET_KEY = "#FlyBizDigital###LordsOfMarket@2022###LeadToRev@@@2022#"
    }
    
    fun validateToken(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token)
            val expiration = claims.expiration
            expiration == null || expiration.after(Date())
        } catch (e: Exception) {
            false
        }
    }
    
    fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .setSigningKey(SECRET_KEY)
            .parseClaimsJws(token)
            .body
    }
    
    fun extractUsername(token: String): String? {
        return try {
            extractAllClaims(token).subject
        } catch (e: Exception) {
            null
        }
    }
    
    fun extractClaim(token: String, claimKey: String): Any? {
        return try {
            extractAllClaims(token)[claimKey]
        } catch (e: Exception) {
            null
        }
    }
}