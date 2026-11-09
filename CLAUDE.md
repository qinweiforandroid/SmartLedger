# Project Rules: KMP Personal Accounting App (Android / iOS / macOS)

## 1. Developer Background & Architecture
- The developer has strong native Android experience (MVVM, Coroutines, Room) but is new to Jetpack Compose.
- Strictly follow standard **MVVM + Repository** architecture inside `composeApp/src/commonMain/kotlin`.
- Use `androidx.lifecycle.ViewModel` (KMP version) with `viewModelScope` and `StateFlow` / `MutableStateFlow` (NEVER use `LiveData`).
- Use `kotlinx.coroutines` for all async operations.

## 2. Strict KMP Platform Boundaries
- **NO Android-only APIs in `commonMain`**: Never import `android.content.Context`, `java.util.Date`, `SimpleDateFormat`, or `android.widget.Toast`.
- Use `kotlinx-datetime` for all date/time formatting and calculations.
- Use Compose `SnackbarHost` for user notifications instead of Toasts.
- When native platform capabilities are truly required (e.g., file export, database builder), define `expect` in `commonMain` and implement `actual` in `androidMain`, `iosMain`, and `desktopMain`.

## 3. UI & Compose Guidelines
- Write all UI in `commonMain` using **Compose Multiplatform (Material 3)**.
- Keep Composable functions clean, modular, and well-commented so an XML developer can easily understand the layout hierarchy (e.g., explain `Column`, `Row`, `Box`, `Modifier`).
- For charts (pie/bar charts), draw them directly using native Compose `Canvas` rather than adding third-party chart libraries.

## 4. Dependency & Token Protection Rules
- **DO NOT** modify `gradle/libs.versions.toml` or `build.gradle.kts` unless explicitly instructed by the user.
- Keep responses concise and output complete, compilable Kotlin files without skipping code with `// ... existing code` comments.
- Modify only the files relevant to the current prompt.
