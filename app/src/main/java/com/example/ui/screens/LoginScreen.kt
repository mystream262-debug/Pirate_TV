package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.AssetUtils
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.BorderDark
import com.example.ui.theme.BrandGoldSerif
import com.example.ui.theme.GoldGradient
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.InputBorderDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    initialUser: String,
    initialPass: String,
    isLoading: Boolean,
    errorMessage: String,
    onLogin: (user: String, pass: String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var user by remember(initialUser) { mutableStateOf(initialUser) }
    var pass by remember(initialPass) { mutableStateOf(initialPass) }
    val focusManager = LocalFocusManager.current
    val hasLogo = AssetUtils.rememberAssetExists("img_logo.png")

    val bgBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF34404F),
            Color(0xFF232A35),
            Color(0xFF14181F)
        ),
        radius = 1200f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .testTag("login_screen")
    ) {
        // Caja de login centrada (lbox)
        Column(
            modifier = Modifier
                .width(360.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo o texto estilizado Pirate_TV
            if (hasLogo) {
                AsyncImage(
                    model = AssetUtils.assetUri("img_logo.png"),
                    contentDescription = "Pirate_TV Logo",
                    modifier = Modifier
                        .width(230.dp)
                        .padding(bottom = 24.dp)
                )
            } else {
                Text(
                    text = "Pirate_TV",
                    color = BrandGoldSerif,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(bottom = 26.dp)
                )
            }

            // Campo Usuario (.linp)
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                placeholder = { Text("Usuario", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = InputBorderDark,
                    focusedContainerColor = Color(0x8C0A0D12),
                    unfocusedContainerColor = Color(0x8C0A0D12)
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("login_username_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Campo Contraseña (.linp)
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                placeholder = { Text("Contraseña", color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = InputBorderDark,
                    focusedContainerColor = Color(0x8C0A0D12),
                    unfocusedContainerColor = Color(0x8C0A0D12)
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (!isLoading) onLogin(user, pass)
                    }
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("login_password_input")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Botón ENTRAR (.lgo) con degradado dorado y tracking
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GoldGradient)
                    .clickable(enabled = !isLoading) {
                        focusManager.clearFocus()
                        onLogin(user, pass)
                    }
                    .testTag("login_submit_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = BackgroundDark,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "ENTRAR",
                        color = BackgroundDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    )
                }
            }

            // Mensaje de estado / error (lmsg)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFE8C986),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Botón Ajustes circular en la esquina inferior derecha (#lgear)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, BorderDark, CircleShape)
                .background(Color(0x590A0D12))
                .clickable { onOpenSettings() }
                .testTag("login_gear_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Ajustes",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
