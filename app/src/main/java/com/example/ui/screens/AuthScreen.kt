package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.remote.onGoogleSignInClicked
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.components.CreatorAvatar
import com.example.ui.components.CreatorPaisaLogoBadge
import com.example.ui.components.LanguageTogglePill
import com.example.ui.components.MonetizationUnavailableBanner
import com.example.ui.viewmodel.AuthUiState

@Composable
fun AuthScreen(
    authState: AuthUiState,
    currentLanguage: AppLanguage,
    onToggleLanguage: () -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onSignUp: (email: String, username: String, password: String, bio: String, avatarUri: String) -> Unit,
    onClearError: () -> Unit,
    isFirebaseCloudMode: Boolean = false,
    onGoogleSignInSuccess: () -> Unit = {},
    onGoogleSignInError: (String) -> Unit = {},
    onForgotPassword: (String, (Result<Unit>) -> Unit) -> Unit = { _, _ -> }
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isGoogleLoading by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetSending by remember { mutableStateOf(false) }
    var resetFeedback by remember { mutableStateOf<String?>(null) }
    var resetIsError by remember { mutableStateOf(false) }

    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf("drawable://img_creator_paisa_logo") }
    var showPassword by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarUri = uri.toString()
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { if (!resetSending) showForgotDialog = false },
            title = {
                Text(
                    text = if (currentLanguage == AppLanguage.HINDI) "पासवर्ड रीसेट करें" else "Reset Password"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (currentLanguage == AppLanguage.HINDI) {
                            "अपना पंजीकृत ईमेल दर्ज करें। हम आपको पासवर्ड रीसेट लिंक भेजेंगे।"
                        } else {
                            "Enter your registered email address and we will send a password reset link."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = {
                            resetEmail = it
                            resetFeedback = null
                        },
                        label = { Text(strings.emailLabel) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_input")
                    )
                    if (resetFeedback != null) {
                        Text(
                            text = resetFeedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (resetIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !resetSending,
                    onClick = {
                        resetSending = true
                        resetFeedback = null
                        onForgotPassword(resetEmail) { res ->
                            resetSending = false
                            res.onSuccess {
                                resetIsError = false
                                resetFeedback = "Password reset link sent to ${resetEmail.trim()}."
                            }.onFailure { err ->
                                resetIsError = true
                                resetFeedback = err.message ?: "Unable to send password reset email."
                            }
                        }
                    },
                    modifier = Modifier.testTag("forgot_password_submit_button")
                ) {
                    if (resetSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (currentLanguage == AppLanguage.HINDI) "लिंक भेजें" else "Send Reset Link")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !resetSending,
                    onClick = { showForgotDialog = false }
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.widthIn(max = 520.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CreatorPaisaLogoBadge(size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LanguageTogglePill(
                        currentLanguage = currentLanguage,
                        onToggle = onToggleLanguage
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                MonetizationUnavailableBanner(showDetail = true)

                Spacer(modifier = Modifier.height(18.dp))

                if (isFirebaseCloudMode) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(22.dp)) {
                            Text(
                                text = if (currentLanguage == AppLanguage.HINDI) {
                                    "क्रिएटर पैसा में साइन इन करें"
                                } else {
                                    "Welcome to Creator Paisa"
                                },
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (currentLanguage == AppLanguage.HINDI) {
                                    "शॉर्ट्स, लॉन्ग वीडियो, सब्सक्रिप्शन, कमेंट्स और रीयल-टाइम सूचनाओं के लिए अपने Google खाते से साइन इन करें।"
                                } else {
                                    "Sign in with your Google account to upload Shorts and long videos, follow creators, comment, and receive real-time notifications."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AnimatedVisibility(visible = authState is AuthUiState.Error) {
                                val errMsg = (authState as? AuthUiState.Error)?.message.orEmpty()
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp)
                                        .testTag("auth_error_banner")
                                ) {
                                    Text(
                                        text = errMsg,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                enabled = !isGoogleLoading && authState !is AuthUiState.Loading,
                                onClick = {
                                    onClearError()
                                    isGoogleLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isGoogleLoading = false
                                            onGoogleSignInSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isGoogleLoading = false
                                            onGoogleSignInError(msg)
                                        },
                                        scope = coroutineScope,
                                        onAuthCancelled = {
                                            isGoogleLoading = false
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("google_signin_button")
                            ) {
                                if (isGoogleLoading || authState is AuthUiState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (currentLanguage == AppLanguage.HINDI) {
                                            "Google से साइन इन करें"
                                        } else {
                                            "Sign in with Google"
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = {
                                    resetFeedback = null
                                    showForgotDialog = true
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("forgot_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockReset,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentLanguage == AppLanguage.HINDI) {
                                        "पासवर्ड भूल गए?"
                                    } else {
                                        "Forgot Password?"
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = if (isSignUpMode) strings.signupTitle else strings.loginTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (isSignUpMode) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                CreatorAvatar(
                                    uri = avatarUri,
                                    contentDescription = "Profile avatar preview",
                                    size = 56.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                OutlinedButton(
                                    onClick = {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("pick_signup_avatar_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Profile Photo")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = it
                                    onClearError()
                                },
                                label = { Text(strings.usernameLabel) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_username_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                onClearError()
                            },
                            label = { Text(strings.emailLabel) },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                onClearError()
                            },
                            label = { Text(strings.passwordLabel) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        if (isSignUpMode) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text(strings.bioLabel) },
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_bio_input")
                            )
                        }

                        AnimatedVisibility(visible = authState is AuthUiState.Error) {
                            val errMsg = (authState as? AuthUiState.Error)?.message.orEmpty()
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .testTag("auth_error_banner")
                            ) {
                                Text(
                                    text = errMsg,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (isSignUpMode) {
                                    onSignUp(email, username, password, bio, avatarUri)
                                } else {
                                    onLogin(email, password)
                                }
                            },
                            enabled = authState !is AuthUiState.Loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit_button")
                        ) {
                            if (authState is AuthUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isSignUpMode) strings.signupButton else strings.loginButton,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                isSignUpMode = !isSignUpMode
                                onClearError()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("auth_mode_toggle_button")
                        ) {
                            Text(
                                text = if (isSignUpMode) strings.switchToLogin else strings.switchToSignup
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.quickTestAccounts,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isSignUpMode = false
                                    email = "aarav@creatorpaisa.test"
                                    password = "Creator@123"
                                    onLogin("aarav@creatorpaisa.test", "Creator@123")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_login_creator")
                            ) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Creator", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    isSignUpMode = false
                                    email = "viewer@creatorpaisa.test"
                                    password = "Viewer@123"
                                    onLogin("viewer@creatorpaisa.test", "Viewer@123")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_login_viewer")
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Viewer", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    isSignUpMode = false
                                    email = "admin@creatorpaisa.test"
                                    password = "Admin@123"
                                    onLogin("admin@creatorpaisa.test", "Admin@123")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_login_admin")
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Admin", maxLines = 1)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
