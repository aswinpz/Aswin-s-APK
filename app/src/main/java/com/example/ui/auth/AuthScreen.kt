package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R

@Composable
fun AuthScreen(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
      IconButton(
        onClick = onToggleTheme,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .minimumInteractiveComponentSize()
          .testTag("auth_theme_toggle_button"),
      ) {
        Icon(
          imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
          contentDescription = stringResource(R.string.toggle_theme_cd),
        )
      }

      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .widthIn(max = 460.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.QrCodeScanner,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }

        Image(
          painter = painterResource(id = R.drawable.img_warehouse_hero),
          contentDescription = "Organized footwear storage bins",
          modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp)),
          contentScale = ContentScale.Crop,
        )

        Text(
          text = stringResource(R.string.sign_in_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )

        Text(
          text = stringResource(R.string.sign_in_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        if (errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = {
                isLoading = false
              },
            )
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.5.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Signing in…")
          } else {
            Text(
              text = stringResource(R.string.sign_in_with_google),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}
