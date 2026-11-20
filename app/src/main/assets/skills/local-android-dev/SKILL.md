---
name: local-android-dev
description: "Production-grade Android app development and XML journey testing: native and cross-platform architecture, UI flows, journey execution, and CI-built releases."
version: 1.0.0
author: User custom skill (ported from Muse)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [android, kotlin, java, mobile, testing, ci, apk]
    related_skills: [local-app-store-design, local-pre-delivery-verification-gate, reverse-engineering-android]
---

# Android Development and UI Journey Testing

## Choose the stack and architecture

## Detailed guide (inlined — the full procedure)

<!-- 2026-11-20 (blanket audit): the text below is the cited reference file, inlined verbatim — on this phone only this SKILL.md is loaded, so a pointer to a sibling file would be a dead end. The same file also ships in this skill folder. -->

# Android App Development Skill - Detailed Guide

> This file contains the detailed procedure and reference material extracted from `SKILL.md` for focused loading. The root skill defines activation, examples, safety constraints, and limitations.

## Overview

This skill guides production-grade Android and cross-platform (non-iOS) app development following practices used at big tech companies. It covers the entire development lifecycle — architecture, UI, code quality, testing, error handling, release, and maintenance.


## §1 Stack Selection

Choose based on team, requirements, and platform targets. **Do not recommend iOS-specific paths.**

### Native Android — Kotlin + Jetpack Compose
**Best for:** Android-only apps, hardware-intensive features, best-in-class UX, new projects.
- Language: **Kotlin**
- UI: **Jetpack Compose** (modern declarative UI)
- Key libs: Room, Retrofit/Ktor, Hilt, WorkManager, DataStore, Navigation Compose
- Full detail: the `native-android` section below.

### Native Android — Java + XML Views
**Best for:** Existing Java codebases, teams without Kotlin experience, legacy app maintenance, incremental Kotlin migration.
- Language: **Java** (fully supported by Google, not deprecated)
- UI: **XML Layouts** (ConstraintLayout, RecyclerView, ViewBinding)
- Key libs: Room, Retrofit, Hilt, WorkManager, LiveData, ViewModel
- Java and Kotlin **coexist seamlessly** in the same project — migrate incrementally
- Full detail: the `java-android` section below.

### Flutter (Dart)
**Best for:** Android + Web (+ desktop) from one codebase, fast iteration, pixel-perfect custom UI.
- Language: **Dart**
- UI: Flutter Widget tree (Material 3 / Cupertino widgets available but target Material for Android)
- Key libs: Provider/Riverpod/Bloc, Dio, Drift/Isar, go_router, flutter_local_notifications
- Full detail: the `flutter` section below.

### React Native (JavaScript/TypeScript)
**Best for:** Web + Android code sharing, JS/TS teams, rich ecosystem.
- Language: **TypeScript** (preferred)
- UI: React Native core components + NativeWind / React Native Paper
- Key libs: React Navigation, Zustand/Redux Toolkit, React Query, MMKV
- Full detail: the `react-native` section below.

### Kotlin Multiplatform (KMM / Compose Multiplatform)
**Best for:** Sharing business logic across Android + Desktop + Web while keeping native Android UI.
- Language: **Kotlin** everywhere
- UI: Native Compose on Android; Compose Multiplatform for shared UI
- Key libs: Ktor, SQLDelight, Koin, kotlinx.serialization, Napier
- Full detail: the `kmm` section below.

### Hybrid (Capacitor / Ionic)
**Best for:** Web-first teams, simple apps, PWA-like content apps.
- Language: TypeScript + HTML/CSS
- UI: Ionic components or custom web UI
- Avoid for: Heavy animations, native sensor access, high-performance games
- Full detail: the `hybrid` section below.

### Decision Matrix

| Requirement | Native Kotlin | Native Java | Flutter | RN | KMM | Hybrid |
|---|---|---|---|---|---|---|
| Android-only (new) | ✅ Best | ✅ | ✅ | ✅ | ✅ | ✅ |
| Android-only (existing Java) | ⚠️ migrate | ✅ Best | ❌ | ❌ | ⚠️ | ❌ |
| Android + Web | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ Best |
| Android + Desktop | ❌ | ❌ | ✅ | ⚠️ | ✅ | ⚠️ |
| Shared business logic only | N/A | N/A | N/A | N/A | ✅ Best | N/A |
| Native performance | ✅ | ✅ | ✅ | ⚠️ | ✅ | ❌ |
| JS/TS team | ❌ | ❌ | ❌ | ✅ Best | ❌ | ✅ |
| Custom pixel-perfect UI | ✅ | ⚠️ | ✅ Best | ⚠️ | ✅ | ❌ |

---


## §2 Architecture

### Core Principle: Separation of Concerns
Every production Android project must separate **UI**, **business logic**, and **data** into distinct, independently testable layers.

### Recommended Architecture: Clean Architecture + MVI/MVVM

```
app/
├── ui/              # Composables / Activities / Fragments / Screen states
├── presentation/    # ViewModels, UI State, UI Events
├── domain/          # Use cases, domain models, repository interfaces
├── data/            # Repository impl, remote (API), local (DB), mappers
└── di/              # Dependency injection modules
```

**Data flow (unidirectional):**
```
User Action → ViewModel/Store → Use Case → Repository → Data Source
                    ↓
             UI State (sealed class / StateFlow)
                    ↓
             Composable / View renders state
```

### Key Architecture Patterns by Stack

**Native (MVVM + MVI):**
- `StateFlow` / `SharedFlow` for reactive state
- `sealed class UiState` + `sealed class UiEvent`
- Hilt for DI, coroutines + Flow for async
- Repository pattern wrapping Room + Retrofit

**Flutter (BLoC or Riverpod):**
- `Bloc` or `Cubit` for business logic isolation
- `AsyncNotifierProvider` (Riverpod) for data + state
- Repositories as abstract classes with impl injected

**React Native (Redux Toolkit or Zustand):**
- RTK Query or React Query for server state
- Zustand slices for client state
- Custom hooks to encapsulate business logic per feature

**KMM:**
- Shared `commonMain` holds domain + data layers
- `expect/actual` for platform-specific implementations
- Kotlin coroutines + Flow bridged to platform (StateFlow on Android)

### Module Structure (Multi-module for large apps)

```
:app            # Entry point, DI wiring
:core:ui        # Design system, shared composables
:core:network   # API client, interceptors
:core:database  # Room / SQLDelight setup
:feature:home
:feature:profile
:feature:settings
```

---


## §3 UI & Design

### Design System First
Before writing screens, define:
1. **Color tokens** — Primary, secondary, surface, on-surface, error; light + dark variants
2. **Typography scale** — Display, headline, title, body, label (Material 3 type system)
3. **Spacing scale** — 4dp grid system (4, 8, 12, 16, 24, 32, 48dp)
4. **Shape tokens** — Corner radii per component family
5. **Component library** — Button, TextField, Card, BottomSheet, TopAppBar, etc.

### Jetpack Compose UI Rules
- Use `MaterialTheme` tokens; never hardcode colors/dimensions
- `CompositionLocal` for theme, locale, haptics
- `remember` / `rememberSaveable` correctly (saveable for UI state surviving rotation)
- Extract large composables into sub-composables; each function ≤ 80 lines
- Use `LazyColumn`/`LazyVerticalGrid` for lists; never `Column` with forEach for large data
- Side effects only in `LaunchedEffect`, `DisposableEffect`, `SideEffect`
- Avoid state hoisting anti-patterns: hoist state to the lowest common ancestor

### Accessibility (Non-Negotiable)
- All interactive elements: `contentDescription` or `semantics { }`
- Min touch target: **48×48dp**
- `TalkBack` compatibility tested before every release
- Dynamic text size support (`sp` not `dp` for text)
- Color contrast ratio ≥ 4.5:1 (WCAG AA)

### Navigation
- **Native:** Navigation Compose with typed `NavHost` and `SafeArgs` equivalent
- **Flutter:** `go_router` with named routes and guards
- **RN:** React Navigation v7 with typed `NavigationProp`
- Deep link handling registered for every screen that can be externally opened
- Back stack managed deliberately — don't push duplicates, use `popUpTo` / `launchSingleTop`

### Responsive & Adaptive UI
- Support all screen sizes: phones, foldables, tablets (`WindowSizeClass`)
- Test at 320dp, 360dp, 411dp, 600dp+, 840dp+ widths
- Foldable hinge awareness via `WindowInfoTracker`
- Edge-to-edge display + `WindowInsets` handling required for Android 15+

---


## Best Practices

### Language Standards

**Kotlin:**
- Prefer `data class`, `sealed class`, `object`, `enum class` appropriately
- No `!!` null assertions — use `?.let`, `?: return`, `requireNotNull` with message
- Coroutines: always specify `CoroutineScope` + `Dispatcher` explicitly; never `GlobalScope`
- Use `@Stable` / `@Immutable` on Compose state classes for smart recomposition

**Java:**
- `@NonNull` / `@Nullable` annotations on every method param and return type
- Never call methods on unchecked objects — null-check explicitly or use `Objects.requireNonNull`
- Always null `binding` reference in Fragment's `onDestroyView()` to prevent memory leaks
- Use `ExecutorService` (not `AsyncTask` — deprecated) for background work; or `LiveData` + Room's built-in threading
- Prefer `ListAdapter` + `DiffUtil` over manual `notifyDataSetChanged()` in RecyclerView
- Use `ViewBinding` — never `findViewById`

**Dart (Flutter):**
- Null safety required — no `!` without explicit null check above
- Immutable state objects with `copyWith`
- `const` constructors on all stateless widgets

**TypeScript (RN):**
- `strict: true` in tsconfig always
- Zod or io-ts for runtime type validation of API responses
- No `any` — use `unknown` and narrow

### Dependency Management
- Pin all dependency versions in `build.gradle.kts` / `pubspec.yaml` / `package.json`
- Audit dependencies monthly for security vulnerabilities
- Avoid transitive dependency conflicts — use dependency resolution strategies
- Keep dependency count minimal — every added lib is a maintenance burden

### Code Review Checklist (PR gate)
- [ ] New public APIs have KDoc / DartDoc / JSDoc
- [ ] No hardcoded strings — use string resources / l10n
- [ ] No hardcoded dimensions or colors outside design tokens
- [ ] No blocking I/O on main thread
- [ ] No memory leaks (no `Activity` context stored in singletons)
- [ ] Coroutine scopes / streams properly cancelled / disposed
- [ ] Feature flag guarding any non-trivial feature

---


## §5 Error Handling

### The Golden Rule
**Never let exceptions propagate to the user silently or crash the app.**

### Error Classification

| Type | Strategy |
|------|----------|
| Network errors | Retry with exponential backoff; show retry UI |
| Auth errors (401/403) | Refresh token → re-request → logout if fails |
| Validation errors | Show inline field errors immediately |
| Data parsing errors | Log + fallback to cached/default state |
| Unexpected crashes | Catch at top-level; show error screen + report |
| Background task failures | Retry via WorkManager; notify user if critical |

### Result / Either Pattern (Kotlin)
```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: AppException) : AppResult<Nothing>()
}

sealed class AppException(msg: String) : Exception(msg) {
    class NetworkException(msg: String) : AppException(msg)
    class AuthException(msg: String) : AppException(msg)
    class ParseException(msg: String) : AppException(msg)
    class UnknownException(msg: String) : AppException(msg)
}
```

Use `AppResult<T>` as return type for all repository + use case functions. ViewModels map to `UiState.Error`.

### Crash Reporting
- Integrate **Firebase Crashlytics** or **Sentry** from day one
- Set user identifiers and custom keys before crash occurs
- Non-fatal exceptions logged for all caught errors
- ANR monitoring enabled
- Crash-free sessions target: **≥ 99.5%**

### Offline / Network Resilience
- Cache-first strategy: show stale data, fetch fresh in background
- `Room` / `Drift` / `MMKV` as single source of truth
- Expose network state via `ConnectivityManager` and reflect in UI
- All network calls wrapped with timeout + retry policy

---


## §6 Testing

### Testing Pyramid

```
         /\
        /E2E\        ← 10%  (UI tests: Espresso, Maestro, Appium)
       /------\
      / Integr \     ← 20%  (Repository, DB, API contract tests)
     /----------\
    /    Unit    \   ← 70%  (ViewModels, Use Cases, Utilities)
   /--------------\
```

### Unit Tests (70%)
- Every ViewModel, UseCase, Repository, Mapper tested
- **Native:** JUnit5 + MockK + Turbine (Flow testing) + Kotest assertions
- **Flutter:** `flutter_test` + `mocktail`
- **RN:** Jest + `@testing-library/react-native` + `msw` for API mocking
- Coverage target: **≥ 80%** on domain + presentation layers

### Integration Tests (20%)
- Room DB tests with in-memory database
- Retrofit/Ktor tests with `MockWebServer` (OkHttp)
- Repository tests verifying cache + remote coordination
- API contract tests against real staging endpoint

### UI / E2E Tests (10%)
- **Espresso** for critical user journeys (login, checkout, core action)
- **Maestro** for cross-platform E2E flows (recommended for Flutter + RN too)
- Run on real device farm (Firebase Test Lab / BrowserStack) before release
- Smoke test suite runs on every PR; full E2E suite nightly

### Test Data Management
- Use factories / builders for test data, never copy-paste objects
- Hermetic tests: never share mutable state between test cases
- Fakes over mocks for complex dependencies (repositories, data sources)

---


## §7 Build & Release

### Build Variants
```
debug       → dev API, logging on, no minification, debuggable
staging     → staging API, logging on, minified, not debuggable
release     → prod API, logging off, minified, signed
```

### Gradle Best Practices (Native)
- `build.gradle.kts` only — no Groovy DSL in new projects
- Version catalog (`libs.versions.toml`) for all dependency versions
- `buildConfig` for environment-specific constants
- Baseline profiles for startup performance
- R8 full mode enabled in release; maintain proguard rules in version control

### CI/CD Pipeline

```
PR Opened
  └─ lint + unit tests + build debug APK          [< 5 min]

Merge to main
  └─ unit + integration tests + staging build     [< 15 min]
  └─ deploy to Firebase App Distribution (QA)

Release tag
  └─ full test suite + E2E on device farm         [< 45 min]
  └─ build release AAB
  └─ upload to Play Console (internal track)
  └─ promote: internal → closed testing → open → production
```

**Recommended CI:** GitHub Actions, Bitrise, or CircleCI.

### Play Store Release Strategy
- Always release to **internal → closed → open testing** before production
- Use **staged rollouts**: 5% → 20% → 50% → 100% with 24-48h monitoring
- Monitor Crashlytics + ANR rate + rating before expanding rollout
- **Never skip staged rollout** for significant changes

### App Signing
- Upload key (Play App Signing): stored in CI secrets, never committed
- Use Google Play App Signing for distribution key management
- Document key recovery procedure in team runbook

---


## §8 Performance

### Startup Performance
- App startup time target: **cold start < 1s**, warm start < 500ms
- Use **App Startup library** for initializing libraries lazily
- Baseline profiles generated + committed to repo
- Heavy initialization moved off main thread

### UI Performance
- Target: **60fps** (90/120fps on supported devices); **zero jank**
- Measure with **Android Studio Profiler** + `FrameMetrics` API
- Avoid allocation in `draw()` / `onMeasure()` / composition
- Use `derivedStateOf` in Compose to avoid unnecessary recompositions
- Image loading: Coil (Compose) / Glide / Picasso — never load full-res in thumbnails

### Memory
- No `Activity` / `Context` references in ViewModels or singletons
- WeakReferences for listeners stored beyond their owner's lifecycle
- Bitmap recycling and memory cache sizing
- Heap dump + leak detection via **LeakCanary** in debug builds (always)

### Network
- HTTP caching headers respected
- Image CDN + WebP format
- Gzip/Brotli compression verified
- Request batching where applicable
- Connection pooling configured

### Battery
- Background work only via **WorkManager** with appropriate constraints
- Location updates: request only needed accuracy level; stop when backgrounded
- Wakelocks used sparingly with explicit release

---


## §9 Debugging & Bug Fixing

### Debugging Process

1. **Reproduce reliably** — document exact steps, device, OS version, account state
2. **Isolate** — is it UI, business logic, network, or persistence?
3. **Instrument** — add targeted logs / breakpoints, NOT shotgun logging
4. **Hypothesize** — form 1-3 specific hypotheses before touching code
5. **Fix the root cause** — never patch symptoms; trace back to the source
6. **Regression test** — write a test that fails before fix, passes after
7. **Document** — comment explaining why the fix works, not just what it does

### Common Android Bug Patterns

| Bug | Likely Cause | Fix |
|-----|-------------|-----|
| ANR | Main thread I/O / long computation | Move to coroutine/Dispatcher.IO |
| Memory leak | Context stored in singleton | Use `applicationContext`; WeakRef |
| Crash on rotation | ViewModel not used; state not saved | `rememberSaveable` / ViewModel |
| UI lag | Recomposition loops | `derivedStateOf`, stable params |
| Blank screen after API call | Error swallowed silently | Check error state propagation |
| Deep link not working | Manifest intent-filter missing | Verify `adb shell am start` test |
| Push notification silent | Background restrictions | Test on real devices across OEMs |

### Logging Standards
- **Production:** Firebase Crashlytics only (no `Log.d` in release builds)
- **Debug/Staging:** Timber with debug tree
- Log levels: ERROR (crashes), WARN (recoverable), INFO (key events), DEBUG (dev only)
- Never log PII — mask emails, phone numbers, tokens in logs

### OEM-Specific Issues
- Test on **Samsung**, **Xiaomi/MIUI**, **OnePlus/OxygenOS**, **Huawei (no GMS)** for critical flows
- Background restrictions vary widely by OEM — test push, alarms, background sync
- Maintain a physical or cloud device farm with top market-share devices

---


## §10 Development Roadmap

Follow this phase structure for any new Android project:

### Phase 0 — Foundation (Week 1-2)
- [ ] Stack decision documented with rationale
- [ ] Module structure defined
- [ ] Design system tokens defined (colors, type, spacing, shapes)
- [ ] CI pipeline running (lint + unit tests + build)
- [ ] Crash reporting integrated (Crashlytics/Sentry)
- [ ] Analytics baseline integrated (Firebase/Amplitude)
- [ ] API contract / mock server set up
- [ ] DI framework configured
- [ ] Navigation skeleton implemented
- [ ] Flavor/build variant config complete

### Phase 1 — Core Features (Weeks 3-8)
- [ ] Auth flow (login, register, token refresh, logout)
- [ ] Core screen shells with real navigation
- [ ] Network layer (client, interceptors, error handling)
- [ ] Local persistence layer (DB schema + DAOs)
- [ ] Repository layer wiring remote + local
- [ ] ViewModels + UI states for each feature
- [ ] Unit tests for all ViewModels + use cases
- [ ] Feature flags infrastructure

### Phase 2 — Polish (Weeks 9-12)
- [ ] Design QA pass against Figma/spec
- [ ] Accessibility audit (TalkBack, contrast, touch targets)
- [ ] Dark mode implementation + verification
- [ ] Localization (strings externalized, RTL support if needed)
- [ ] Loading, empty, error states on every screen
- [ ] Deep link handling
- [ ] Widget / notification implementation
- [ ] Offline mode verification

### Phase 3 — Hardening (Weeks 12-14)
- [ ] Performance profiling (startup, scroll, memory)
- [ ] E2E test suite on device farm (Firebase Test Lab)
- [ ] Security review (certificate pinning, biometrics, secure storage)
- [ ] Proguard / R8 rules verified
- [ ] Crash-free rate ≥ 99.5% on staging
- [ ] Play Store listing, screenshots, privacy policy

### Phase 4 — Release
- [ ] AAB signed and uploaded to internal track
- [ ] Staged rollout plan defined
- [ ] Monitoring dashboard set up (Crashlytics, Play Console vitals)
- [ ] Rollback plan documented
- [ ] On-call rotation assigned

### Phase 5 — Post-Launch (Ongoing)
- Crash-free rate monitored daily
- ANR rate < 0.47% (Play Store threshold)
- App rating monitored; negative reviews triaged weekly
- Dependency updates reviewed monthly
- OS beta testing with each new Android release

---


## Additional Resources

For stack-specific deep dives, read:
- `native-android` section below — Kotlin, Compose, Room, Hilt, Coroutines
- `java-android` section below — Java, XML Views, ViewBinding, LiveData, Retrofit, Room, Hilt, migration path
- `flutter` section below — Dart, BLoC/Riverpod, Drift, go_router
- `react-native` section below — TypeScript, RN architecture, Hermes, New Architecture
- `kmm` section below — KMM shared modules, SQLDelight, Ktor, Compose Multiplatform
- `hybrid` section below — Capacitor, Ionic, PWA considerations

## native-android (references/native-android.md — inlined)


## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com.example.app/
│   │   │   ├── MyApp.kt                     # Application class, Hilt entry point
│   │   │   ├── MainActivity.kt              # Single activity, NavHost host
│   │   │   ├── ui/
│   │   │   │   ├── theme/                   # MaterialTheme, Color, Type, Shape
│   │   │   │   ├── components/              # Shared design system composables
│   │   │   │   └── feature/
│   │   │   │       ├── home/
│   │   │   │       │   ├── HomeScreen.kt
│   │   │   │       │   ├── HomeViewModel.kt
│   │   │   │       │   └── HomeUiState.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/                   # Domain models (pure Kotlin, no Android deps)
│   │   │   │   ├── repository/              # Interfaces only
│   │   │   │   └── usecase/                 # One class per use case
│   │   │   ├── data/
│   │   │   │   ├── remote/                  # Retrofit services, DTOs, mappers
│   │   │   │   ├── local/                   # Room DB, DAOs, entities
│   │   │   │   └── repository/              # Repository implementations
│   │   │   └── di/                          # Hilt modules
│   └── test/                                # Unit tests
│   └── androidTest/                         # Instrumented tests
├── build.gradle.kts
└── proguard-rules.pro
```

## ViewModel Pattern

```kotlin
// UiState — sealed class for exhaustive when()
sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val items: List<Item>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

// UiEvent — one-shot events (navigation, snackbars)
sealed class HomeUiEvent {
    data class NavigateTo(val route: String) : HomeUiEvent()
    data class ShowSnackbar(val message: String) : HomeUiEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getItemsUseCase: GetItemsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<HomeUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init { loadItems() }

    fun loadItems() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            getItemsUseCase()
                .onSuccess { _uiState.value = HomeUiState.Success(it) }
                .onFailure { _uiState.value = HomeUiState.Error(it.message ?: "Unknown error") }
        }
    }
}
```

## Repository Pattern

```kotlin
// Interface in domain layer
interface ItemRepository {
    fun observeItems(): Flow<List<Item>>
    suspend fun refreshItems(): Result<Unit>
    suspend fun getItemById(id: String): Result<Item>
}

// Implementation in data layer
class ItemRepositoryImpl @Inject constructor(
    private val remoteSource: ItemRemoteDataSource,
    private val localSource: ItemLocalDataSource,
    private val mapper: ItemMapper
) : ItemRepository {

    override fun observeItems(): Flow<List<Item>> =
        localSource.observeAll().map { mapper.toDomain(it) }

    override suspend fun refreshItems(): Result<Unit> = runCatching {
        val dto = remoteSource.fetchItems()
        localSource.insertAll(mapper.toEntity(dto))
    }

    override suspend fun getItemById(id: String): Result<Item> = runCatching {
        // Example implementation fetching from local cache
        val entity = localSource.getById(id) ?: throw Exception("Item not found")
        mapper.toDomain(entity)
    }
}
```

## Compose Screen

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // One-shot event handling
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is HomeUiEvent.NavigateTo -> onNavigate(event.route)
                is HomeUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when (val state = uiState) {
            is HomeUiState.Loading -> LoadingContent()
            is HomeUiState.Success -> HomeContent(state.items, Modifier.padding(padding))
            is HomeUiState.Error -> ErrorContent(state.message, onRetry = viewModel::loadItems)
        }
    }
}
```

## Room Database

```kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}

@Database(entities = [ItemEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
```

## Hilt DI Setup

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(buildOkHttpClient())
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository
}
```

## Key Dependencies (libs.versions.toml)

```toml
[versions]
kotlin = "2.0.0"
compose-bom = "2024.06.00"
hilt = "2.51"
room = "2.6.1"
retrofit = "2.11.0"
coroutines = "1.8.1"
lifecycle = "2.8.2"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
```

## Testing Setup

```kotlin
// ViewModel unit test
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getItemsUseCase = mockk<GetItemsUseCase>()
    private lateinit var viewModel: HomeViewModel

    @BeforeEach
    fun setup() { viewModel = HomeViewModel(getItemsUseCase) }

    @Test
    fun `loadItems emits Success when use case succeeds`() = runTest {
        val items = listOf(Item("1", "Test"))
        coEvery { getItemsUseCase() } returns Result.success(items)

        viewModel.uiState.test {
            skipItems(1) // Loading
            assertThat(awaitItem()).isEqualTo(HomeUiState.Success(items))
        }
    }
}
```

## java-android (references/java-android.md — inlined)


## When to Use Java

Java remains fully supported by Android and Google. Use it when:
- Maintaining or extending an existing Java codebase
- Team is Java-fluent without Kotlin experience
- Integrating Java-only SDKs or legacy modules
- Gradual migration: new Kotlin modules alongside old Java modules

> **Java + Kotlin interop is seamless** — you can have both in the same project. New files can be Kotlin while legacy files stay Java.

---

## Project Structure

```
app/src/main/java/com/example/app/
├── MyApp.java                   # Application class
├── MainActivity.java            # Host activity
├── ui/
│   └── home/
│       ├── HomeActivity.java    # OR Fragment-based
│       ├── HomeFragment.java
│       └── HomeAdapter.java
├── viewmodel/
│   └── HomeViewModel.java
├── repository/
│   └── ItemRepository.java
├── data/
│   ├── remote/
│   │   ├── ApiService.java      # Retrofit interface
│   │   ├── ApiClient.java       # OkHttp + Retrofit setup
│   │   └── dto/ItemDto.java
│   └── local/
│       ├── AppDatabase.java     # Room database
│       ├── ItemDao.java
│       └── entity/ItemEntity.java
├── model/
│   └── Item.java                # Domain model
└── di/                          # Manual DI or Hilt
```

---

## ViewModel (Java + LiveData)

```java
public class HomeViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<Item>>> _uiState =
        new MutableLiveData<>(UiState.loading());

    public LiveData<UiState<List<Item>>> uiState = _uiState;

    private final ItemRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Constructor injection (Hilt or manual)
    public HomeViewModel(ItemRepository repository) {
        this.repository = repository;
        loadItems();
    }

    public void loadItems() {
        _uiState.setValue(UiState.loading());
        executor.execute(() -> {
            try {
                List<Item> items = repository.getItems();
                _uiState.postValue(UiState.success(items));
            } catch (Exception e) {
                _uiState.postValue(UiState.error(e.getMessage()));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
```

---

## UiState Wrapper

```java
public class UiState<T> {
    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    public final T data;
    public final String errorMessage;

    private UiState(Status status, T data, String errorMessage) {
        this.status = status;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public static <T> UiState<T> loading() {
        return new UiState<>(Status.LOADING, null, null);
    }

    public static <T> UiState<T> success(T data) {
        return new UiState<>(Status.SUCCESS, data, null);
    }

    public static <T> UiState<T> error(String message) {
        return new UiState<>(Status.ERROR, null, message);
    }

    public boolean isLoading() { return status == Status.LOADING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError()   { return status == Status.ERROR; }
}
```

---

## Fragment Observing ViewModel

```java
public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private FragmentHomeBinding binding; // ViewBinding

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this,
            new HomeViewModelFactory(new ItemRepository(requireContext())))
            .get(HomeViewModel.class);

        viewModel.uiState.observe(getViewLifecycleOwner(), state -> {
            binding.progressBar.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
            binding.recyclerView.setVisibility(state.isSuccess() ? View.VISIBLE : View.GONE);
            binding.errorView.setVisibility(state.isError() ? View.VISIBLE : View.GONE);

            if (state.isSuccess()) {
                adapter.submitList(state.data);
            }
            if (state.isError()) {
                binding.errorText.setText(state.errorMessage);
            }
        });

        binding.retryButton.setOnClickListener(v -> viewModel.loadItems());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // CRITICAL — avoid memory leak
    }
}
```

---

## Room Database (Java)

```java
// Entity
@Entity(tableName = "items")
public class ItemEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String title;
    public long updatedAt;

    public ItemEntity(@NonNull String id, String title, long updatedAt) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
    }
}

// DAO
@Dao
public interface ItemDao {
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    LiveData<List<ItemEntity>> observeAll();

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    List<ItemEntity> getAll(); // blocking — call off main thread

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ItemEntity> items);

    @Query("DELETE FROM items")
    void deleteAll();
}

// Database
@Database(entities = {ItemEntity.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract ItemDao itemDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "app_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
```

---

## Retrofit API Client (Java)

```java
// Interface
public interface ApiService {
    @GET("items")
    Call<List<ItemDto>> getItems();

    @GET("items/{id}")
    Call<ItemDto> getItemById(@Path("id") String id);

    @POST("items")
    Call<ItemDto> createItem(@Body ItemDto item);
}

// Client setup
public class ApiClient {
    private static final String BASE_URL = BuildConfig.API_BASE_URL;
    private static ApiService INSTANCE;

    public static ApiService getInstance() {
        if (INSTANCE == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(new HttpLoggingInterceptor()
                    .setLevel(BuildConfig.DEBUG
                        ? HttpLoggingInterceptor.Level.BODY
                        : HttpLoggingInterceptor.Level.NONE))
                .build();

            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

            INSTANCE = retrofit.create(ApiService.class);
        }
        return INSTANCE;
    }
}

// Auth interceptor
public class AuthInterceptor implements Interceptor {
    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        String token = TokenStorage.getInstance().getToken();
        Request request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer " + token)
            .build();
        return chain.proceed(request);
    }
}
```

---

## Repository (Java)

```java
public class ItemRepository {
    private final ItemDao itemDao;
    private final ApiService apiService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ItemRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.itemDao = db.itemDao();
        this.apiService = ApiClient.getInstance();
    }

    // Synchronous fetch for ViewModel executor
    public List<Item> getItems() throws Exception {
        Response<List<ItemDto>> response = apiService.getItems().execute();
        if (response.isSuccessful() && response.body() != null) {
            return response.body().stream()
                .map(ItemMapper::toDomain)
                .collect(Collectors.toList());
        } else {
            throw new IOException("HTTP " + response.code());
        }
    }

    // Observe cached data (returns LiveData — auto updates UI)
    public LiveData<List<Item>> observeItems() {
        return Transformations.map(itemDao.observeAll(), entities ->
            entities.stream().map(ItemMapper::toDomain).collect(Collectors.toList())
        );
    }

    // Refresh from network (call from background thread or executor)
    public void refreshItems(Callback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<List<ItemDto>> response = apiService.getItems().execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<ItemEntity> entities = response.body().stream()
                        .map(ItemMapper::toEntity)
                        .collect(Collectors.toList());
                    itemDao.deleteAll();
                    itemDao.insertAll(entities);
                    callback.onSuccess(null);
                } else {
                    callback.onError(new IOException("HTTP " + response.code()));
                }
            } catch (IOException e) {
                callback.onError(e);
            }
        });
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }
}
```

---

## RecyclerView Adapter (Java)

```java
public class ItemAdapter extends ListAdapter<Item, ItemAdapter.ItemViewHolder> {

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Item item);
    }

    public ItemAdapter(OnItemClickListener listener) {
        super(new DiffUtil.ItemCallback<Item>() {
            @Override
            public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
                return a.getId().equals(b.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
                return a.equals(b);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRowBinding binding = ItemRowBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false);
        return new ItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final ItemRowBinding binding;

        ItemViewHolder(ItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item, OnItemClickListener listener) {
            binding.titleText.setText(item.getTitle());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
```

---

## XML Layout Best Practices (Java projects)

```xml
<!-- Use ConstraintLayout — flat hierarchy = better performance -->
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Always use ?attr/ tokens from MaterialTheme, never hardcoded colors -->
    <TextView
        android:id="@+id/titleText"
        android:textColor="?attr/colorOnSurface"
        android:textAppearance="?attr/textAppearanceTitleMedium"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- Always use **ViewBinding** (not `findViewById`, not DataBinding for simple cases)
- Enable in `build.gradle.kts`: `viewBinding { enable = true }`
- Null `binding` in `onDestroyView()` to prevent Fragment memory leaks

---

## Error Handling (Java)

```java
// Checked exceptions: always handle explicitly
public Result<List<Item>> getItemsSafe() {
    try {
        Response<List<ItemDto>> response = apiService.getItems().execute();
        if (!response.isSuccessful()) {
            return Result.failure(new HttpException(response));
        }
        List<Item> items = Objects.requireNonNull(response.body())
            .stream().map(ItemMapper::toDomain).collect(Collectors.toList());
        return Result.success(items);
    } catch (IOException e) {
        return Result.failure(new NetworkException("Network error", e));
    } catch (NullPointerException e) {
        return Result.failure(new ParseException("Empty response body", e));
    }
}

// Custom exception hierarchy
public class AppException extends Exception {
    public AppException(String message) { super(message); }
    public AppException(String message, Throwable cause) { super(message, cause); }
}
public class NetworkException extends AppException { ... }
public class ParseException extends AppException { ... }
public class AuthException extends AppException { ... }
```

---

## Hilt DI (Java)

```java
// Application
@HiltAndroidApp
public class MyApp extends Application {}

// Activity / Fragment — annotate for injection
@AndroidEntryPoint
public class HomeFragment extends Fragment {
    @Inject
    ItemRepository repository; // injected by Hilt
}

// ViewModel
@HiltViewModel
public class HomeViewModel extends ViewModel {
    private final ItemRepository repository;

    @Inject
    public HomeViewModel(ItemRepository repository) {
        this.repository = repository;
    }
}

// Module
@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {
    @Provides
    @Singleton
    public AppDatabase provideDatabase(@ApplicationContext Context context) {
        return AppDatabase.getInstance(context);
    }

    @Provides
    public ItemDao provideItemDao(AppDatabase db) {
        return db.itemDao();
    }
}
```

---

## Unit Testing (Java)

```java
@ExtendWith(MockitoExtension.class)
class HomeViewModelTest {

    @Mock
    ItemRepository mockRepository;

    HomeViewModel viewModel;

    @BeforeEach
    void setup() {
        viewModel = new HomeViewModel(mockRepository);
    }

    @Test
    void loadItems_success_emitsSuccessState() throws Exception {
        List<Item> items = Arrays.asList(new Item("1", "Test"));
        when(mockRepository.getItems()).thenReturn(items);

        viewModel.loadItems();

        // Wait for executor — use CountDownLatch or InstantExecutorRule
        UiState<List<Item>> state = viewModel.uiState.getValue();
        assertNotNull(state);
        assertTrue(state.isSuccess());
        assertEquals(items, state.data);
    }

    @Test
    void loadItems_failure_emitsErrorState() throws Exception {
        when(mockRepository.getItems()).thenThrow(new IOException("Network error"));

        viewModel.loadItems();

        UiState<List<Item>> state = viewModel.uiState.getValue();
        assertNotNull(state);
        assertTrue(state.isError());
    }
}
```

---

## Java → Kotlin Migration Path

When migrating a Java project to Kotlin incrementally:

1. **New files in Kotlin** — Java and Kotlin coexist seamlessly
2. **Convert utilities first** — `@JvmStatic`, `@JvmField` for interop
3. **Convert data models** — Java POJOs → Kotlin `data class`
4. **Convert DAOs and Repositories** — add `suspend` + `Flow`
5. **Convert ViewModels last** — swap `LiveData` + `MutableLiveData` for `StateFlow`
6. **Convert Activities/Fragments** — migrate to Compose screen by screen
7. Annotate Kotlin with `@JvmOverloads`, `@JvmName` where Java callers exist

```kotlin
// Kotlin data class replacing a Java POJO
data class Item(
    val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis()
)

// Kotlin extension to consume Java LiveData from Kotlin cleanly
fun <T> LiveData<T>.observeNonNull(owner: LifecycleOwner, observer: (T) -> Unit) {
    observe(owner) { it?.let(observer) }
}
```

## flutter (references/flutter.md — inlined)


## Project Structure

```
lib/
├── main.dart                    # Entry point
├── app/
│   ├── app.dart                 # MaterialApp + router setup
│   ├── theme/                   # ThemeData, colors, typography, spacing
│   └── router/                  # go_router config, guards
├── features/
│   └── home/
│       ├── data/
│       │   ├── datasource/      # Remote + local data sources
│       │   ├── dto/             # JSON models (freezed)
│       │   └── repository/      # Repo implementations
│       ├── domain/
│       │   ├── model/           # Domain models (freezed)
│       │   ├── repository/      # Abstract repo interfaces
│       │   └── usecase/         # Use cases
│       └── presentation/
│           ├── bloc/            # Bloc/Cubit + state + event
│           └── screen/          # Widgets + page files
├── core/
│   ├── network/                 # Dio client, interceptors
│   ├── database/                # Drift DB setup
│   ├── widgets/                 # Shared design system widgets
│   └── error/                   # Failure types, error handling
└── injection.dart               # GetIt service locator setup
```

## State Management (BLoC)

```dart
// States
@freezed
class HomeState with _$HomeState {
  const factory HomeState.initial() = _Initial;
  const factory HomeState.loading() = _Loading;
  const factory HomeState.success(List<Item> items) = _Success;
  const factory HomeState.failure(String message) = _Failure;
}

// Events
@freezed
class HomeEvent with _$HomeEvent {
  const factory HomeEvent.loadItems() = _LoadItems;
  const factory HomeEvent.refreshItems() = _RefreshItems;
}

// Bloc
class HomeBloc extends Bloc<HomeEvent, HomeState> {
  final GetItemsUseCase _getItems;

  HomeBloc(this._getItems) : super(const HomeState.initial()) {
    on<_LoadItems>(_onLoad);
  }

  Future<void> _onLoad(_LoadItems event, Emitter<HomeState> emit) async {
    emit(const HomeState.loading());
    final result = await _getItems();
    result.fold(
      (failure) => emit(HomeState.failure(failure.message)),
      (items) => emit(HomeState.success(items)),
    );
  }
}
```

## State Management (Riverpod — alternative)

```dart
@riverpod
class HomeNotifier extends _$HomeNotifier {
  @override
  FutureOr<List<Item>> build() => _load();

  Future<List<Item>> _load() async {
    final repo = ref.read(itemRepositoryProvider);
    return repo.getItems().getOrThrow();
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(_load);
  }
}
```

## Screen Widget Pattern

```dart
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (ctx) => sl<HomeBloc>()..add(const HomeEvent.loadItems()),
      child: const _HomeView(),
    );
  }
}

class _HomeView extends StatelessWidget {
  const _HomeView();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: BlocConsumer<HomeBloc, HomeState>(
        listener: (ctx, state) {
          state.maybeWhen(
            failure: (msg) => ScaffoldMessenger.of(ctx)
                .showSnackBar(SnackBar(content: Text(msg))),
            orElse: () {},
          );
        },
        builder: (ctx, state) => state.when(
          initial: () => const SizedBox(),
          loading: () => const Center(child: CircularProgressIndicator()),
          success: (items) => _ItemList(items: items),
          failure: (msg) => ErrorView(message: msg,
              onRetry: () => ctx.read<HomeBloc>().add(
                const HomeEvent.loadItems())),
        ),
      ),
    );
  }
}
```

## go_router Setup

```dart
final router = GoRouter(
  initialLocation: '/home',
  redirect: (context, state) {
    final isLoggedIn = ref.read(authStateProvider).isLoggedIn;
    if (!isLoggedIn && !state.matchedLocation.startsWith('/auth')) {
      return '/auth/login';
    }
    return null;
  },
  routes: [
    GoRoute(
      path: '/home',
      name: AppRoutes.home,
      builder: (ctx, state) => const HomeScreen(),
      routes: [
        GoRoute(
          path: 'detail/:id',
          builder: (ctx, state) =>
              DetailScreen(id: state.pathParameters['id']!),
        ),
      ],
    ),
  ],
);
```

## Drift Database

```dart
@DriftDatabase(tables: [Items])
class AppDatabase extends _$AppDatabase {
  AppDatabase(QueryExecutor e) : super(e);

  @override
  int get schemaVersion => 1;

  Stream<List<Item>> watchAllItems() =>
      (select(items)..orderBy([(t) => OrderingTerm.desc(t.updatedAt)])).watch();

  Future<void> upsertItems(List<ItemsCompanion> rows) =>
      batch((b) => b.insertAllOnConflictUpdate(items, rows));
}
```

## Key pubspec.yaml Dependencies

```yaml
dependencies:
  flutter_bloc: ^8.1.5
  freezed_annotation: ^2.4.1
  riverpod: ^2.5.1                # alternative to bloc
  flutter_riverpod: ^2.5.1
  go_router: ^14.1.0
  dio: ^5.4.3
  drift: ^2.18.0
  sqflite: ^2.3.3
  get_it: ^7.7.0
  injectable: ^2.4.1
  dartz: ^0.10.1                  # Either/Option for FP error handling
  json_annotation: ^4.9.0

dev_dependencies:
  build_runner: ^2.4.9
  freezed: ^2.5.2
  json_serializable: ^6.8.0
  drift_dev: ^2.18.0
  mocktail: ^1.0.3
  bloc_test: ^9.1.7
```

## Error Handling (Either/Failure pattern)

```dart
abstract class Failure {
  final String message;
  const Failure(this.message);
}

class NetworkFailure extends Failure {
  const NetworkFailure([super.message = 'Network error occurred']);
}

class CacheFailure extends Failure {
  const CacheFailure([super.message = 'Cache error occurred']);
}

// Repository
Future<Either<Failure, List<Item>>> getItems() async {
  try {
    final remote = await _remoteSource.fetchItems();
    await _localSource.saveItems(remote);
    return Right(remote.map(_mapper.toDomain).toList());
  } on DioException catch (e) {
    return Left(NetworkFailure(e.message ?? 'Network error'));
  } on Exception {
    return const Left(CacheFailure());
  }
}
```

## Testing

```dart
void main() {
  group('HomeBloc', () {
    late HomeBloc bloc;
    late MockGetItemsUseCase mockUseCase;

    setUp(() {
      mockUseCase = MockGetItemsUseCase();
      bloc = HomeBloc(mockUseCase);
    });

    tearDown(() => bloc.close());

    blocTest<HomeBloc, HomeState>(
      'emits [loading, success] when loadItems succeeds',
      build: () {
        when(() => mockUseCase()).thenAnswer(
          (_) async => Right([Item(id: '1', title: 'Test')]),
        );
        return bloc;
      },
      act: (b) => b.add(const HomeEvent.loadItems()),
      expect: () => [
        const HomeState.loading(),
        isA<HomeState>().having((s) => s, 'success',
            const HomeState.success([Item(id: '1', title: 'Test')])),
      ],
    );
  });
}
```

## react-native (references/react-native.md — inlined)


## Project Structure

```
src/
├── app/
│   ├── App.tsx                  # Root component, providers
│   ├── navigation/              # React Navigation stacks + types
│   └── store/                   # RTK store setup
├── features/
│   └── home/
│       ├── api/                 # RTK Query endpoints
│       ├── components/          # Screen-specific components
│       ├── hooks/               # Feature-level custom hooks
│       ├── screens/             # Screen components
│       ├── store/               # Zustand slice or RTK slice
│       └── types.ts             # Feature types
├── shared/
│   ├── components/              # Design system components
│   ├── hooks/                   # Shared hooks
│   ├── theme/                   # Colors, typography, spacing constants
│   └── utils/                   # Utilities
└── services/
    ├── api/                     # Axios/fetch client + interceptors
    └── storage/                 # MMKV wrapper
```

## Navigation Setup (React Navigation v7)

```typescript
export type RootStackParamList = {
  Auth: undefined;
  Home: undefined;
  Detail: { id: string };
  Settings: undefined;
};

export type RootStackScreenProps<T extends keyof RootStackParamList> =
  NativeStackScreenProps<RootStackParamList, T>;

const Stack = createNativeStackNavigator<RootStackParamList>();

export const RootNavigator = () => {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {isLoggedIn ? (
        <>
          <Stack.Screen name="Home" component={HomeScreen} />
          <Stack.Screen name="Detail" component={DetailScreen} />
        </>
      ) : (
        <Stack.Screen name="Auth" component={AuthScreen} />
      )}
    </Stack.Navigator>
  );
};
```

## State Management (Zustand + React Query)

```typescript
// Client state — Zustand
// Do not persist bearer or refresh tokens in AsyncStorage/plain MMKV.
// Store secrets with a platform-backed module such as react-native-keychain
// or expo-secure-store, and persist only non-sensitive UI state here.
interface AuthState {
  isLoggedIn: boolean;
  setLoggedIn: (value: boolean) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      isLoggedIn: false,
      setLoggedIn: (value) => set({ isLoggedIn: value }),
      logout: () => set({ isLoggedIn: false }),
    }),
    { name: 'auth-ui-storage', storage: createJSONStorage(() => mmkvStorage) }
  )
);

// Keep tokens outside persisted app state.
const getSecureToken = () => Keychain.getGenericPassword().then((r) => (r ? r.password : null));
const saveSecureToken = (token: string) => Keychain.setGenericPassword('auth', token);
const clearSecureToken = () => Keychain.resetGenericPassword();

// Server state — React Query
export const useItems = () =>
  useQuery({
    queryKey: ['items'],
    queryFn: itemsApi.getAll,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

export const useRefreshItems = () =>
  useMutation({
    mutationFn: itemsApi.refresh,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['items'] }),
  });
```

## Screen Pattern

```typescript
type HomeScreenProps = RootStackScreenProps<'Home'>;

export const HomeScreen: FC<HomeScreenProps> = ({ navigation }) => {
  const { data: items, isLoading, isError, refetch } = useItems();

  if (isLoading) return <LoadingView />;
  if (isError) return <ErrorView onRetry={refetch} />;

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={items}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <ItemCard
            item={item}
            onPress={() => navigation.navigate('Detail', { id: item.id })}
          />
        )}
        ListEmptyComponent={<EmptyView />}
        refreshControl={
          <RefreshControl refreshing={isLoading} onRefresh={refetch} />
        }
      />
    </SafeAreaView>
  );
};
```

## API Client (Axios with interceptors)

```typescript
const apiClient = axios.create({
  baseURL: Config.API_BASE_URL,
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
});

// Auth token injection
apiClient.interceptors.request.use(async (config) => {
  const token = await getSecureToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Token refresh on 401
apiClient.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    if (error.response?.status === 401) {
      const newToken = await refreshToken();
      if (newToken) {
        await saveSecureToken(newToken);
        useAuthStore.getState().setLoggedIn(true);
        return apiClient(error.config!);
      }
      await clearSecureToken();
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  }
);
```

## API Response Validation (Zod)

```typescript
const ItemSchema = z.object({
  id: z.string(),
  title: z.string(),
  description: z.string().optional(),
  createdAt: z.string().datetime(),
});

const ItemsResponseSchema = z.array(ItemSchema);
type Item = z.infer<typeof ItemSchema>;

const getItems = async (): Promise<Item[]> => {
  const { data } = await apiClient.get('/items');
  return ItemsResponseSchema.parse(data); // throws ZodError on invalid shape
};
```

## Key Dependencies

```json
{
  "dependencies": {
    "react-native": "0.74.x",
    "@react-navigation/native": "^7.0.0",
    "@react-navigation/native-stack": "^7.0.0",
    "@tanstack/react-query": "^5.45.0",
    "zustand": "^4.5.4",
    "axios": "^1.7.2",
    "zod": "^3.23.8",
    "react-native-keychain": "^8.2.0",
    "react-native-mmkv": "^2.12.2",
    "react-native-safe-area-context": "^4.10.1",
    "react-native-screens": "^3.32.0"
  },
  "devDependencies": {
    "typescript": "^5.4.5",
    "@testing-library/react-native": "^12.5.1",
    "msw": "^2.3.1",
    "jest": "^29.7.0"
  }
}
```

## New Architecture (Bridgeless) Notes
- Enable New Architecture in `android/gradle.properties`: `newArchEnabled=true`
- Use TurboModules for native modules; avoid legacy NativeModules API
- Use Fabric for custom native views
- Test with Hermes JS engine always enabled

## Performance Tips
- Use `useCallback` + `memo` on `renderItem` / list item components
- `FlatList` `windowSize`, `initialNumToRender`, `maxToRenderPerBatch` tuned
- Avoid anonymous inline functions in JSX
- `InteractionManager.runAfterInteractions` for heavy post-navigation work
- `react-native-reanimated` for 60fps animations (runs on UI thread)

## Testing

```typescript
describe('HomeScreen', () => {
  it('shows items when query succeeds', async () => {
    server.use(
      http.get(`${API_URL}/items`, () =>
        HttpResponse.json([{ id: '1', title: 'Test Item' }])
      )
    );

    const { getByText } = render(
      <QueryClientProvider client={testQueryClient}>
        <HomeScreen navigation={mockNavigation} route={mockRoute} />
      </QueryClientProvider>
    );

    expect(await findByText('Test Item')).toBeTruthy();
  });
});
```

## kmm (references/kmm.md — inlined)


## Project Structure

```
project/
├── shared/                          # Shared KMM module
│   ├── src/
│   │   ├── commonMain/kotlin/       # Business logic, domain, data
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   ├── repository/      # Interfaces
│   │   │   │   └── usecase/
│   │   │   ├── data/
│   │   │   │   ├── remote/          # Ktor client + DTOs
│   │   │   │   ├── local/           # SQLDelight DAOs
│   │   │   │   └── repository/      # Implementations
│   │   │   └── di/                  # Koin modules
│   │   ├── androidMain/kotlin/      # Android-specific actual implementations
│   │   └── iosMain/kotlin/          # iOS-specific actual (if needed)
│   └── build.gradle.kts
├── androidApp/                      # Android app module
│   ├── src/main/java/
│   │   ├── ui/                      # Jetpack Compose screens
│   │   ├── presentation/            # Android ViewModels
│   │   └── di/                      # Android-specific DI
│   └── build.gradle.kts
└── build.gradle.kts
```

## Shared Module: Ktor HTTP Client

```kotlin
// commonMain
expect fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

// androidMain
actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        config(this)
        engine { addInterceptor(/* logging, auth */) }
    }

// Shared usage
val client = httpClient {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    defaultRequest {
        url(BuildKonfig.BASE_URL)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
    }
}
```

## SQLDelight Setup

```sql
-- ItemEntity.sq
CREATE TABLE ItemEntity (
    id TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    updatedAt INTEGER NOT NULL DEFAULT 0
);

selectAll:
SELECT * FROM ItemEntity ORDER BY updatedAt DESC;

upsertItem:
INSERT OR REPLACE INTO ItemEntity (id, title, updatedAt)
VALUES (?, ?, ?);
```

```kotlin
// commonMain — Database driver expect/actual
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(AppDatabase.Schema, context, "app.db")
}
```

## Shared Repository

```kotlin
// commonMain
class ItemRepositoryImpl(
    private val remoteSource: ItemRemoteDataSource,
    private val localSource: ItemLocalDataSource,
) : ItemRepository {

    override fun observeItems(): Flow<List<Item>> =
        localSource.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun refreshItems(): Result<Unit> = runCatching {
        val items = remoteSource.fetchItems()
        localSource.upsertAll(items.map { it.toEntity() })
    }
}
```

## Android ViewModel consuming shared Flow

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeItems: ObserveItemsUseCase,    // from shared module
    private val refreshItems: RefreshItemsUseCase     // from shared module
) : ViewModel() {

    val uiState = observeItems()
        .map { HomeUiState.Success(it) as HomeUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading
        )
}
```

## Koin DI (Shared + Android)

```kotlin
// commonMain — shared Koin modules
val sharedModule = module {
    single { DatabaseDriverFactory(get()) }
    single { AppDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single<ItemRepository> { ItemRepositoryImpl(get(), get()) }
    factory { ObserveItemsUseCase(get()) }
    factory { RefreshItemsUseCase(get()) }
}

// androidApp — Android-specific module
val androidModule = module {
    single<Context> { androidApplication() }
    viewModel { HomeViewModel(get(), get()) }
}

// Application class
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApp)
            modules(sharedModule, androidModule)
        }
    }
}
```

## Key Gradle Dependencies (shared/build.gradle.kts)

```kotlin
kotlin {
    androidTarget()
    // Add other targets as needed (jvm, iosArm64, etc.)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
    }
}
```

## Compose Multiplatform (for shared UI)

Use when you want to share UI across Android + Desktop + Web:

```kotlin
// commonMain — shared composable
@Composable
fun HomeScreenContent(
    state: HomeUiState,
    onRetry: () -> Unit
) {
    when (state) {
        is HomeUiState.Loading -> CircularProgressIndicator()
        is HomeUiState.Success -> ItemList(state.items)
        is HomeUiState.Error -> ErrorView(state.message, onRetry)
    }
}

// androidApp — wraps with Android ViewModel
@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(state, onRetry = viewModel::refresh)
}
```

## hybrid (references/hybrid.md — inlined)


## When to Use Hybrid

✅ Good fit:
- Web team building a companion Android app
- Content-heavy apps (news, docs, forms)
- PWA upgrade to installable app
- Rapid prototyping

❌ Avoid for:
- Real-time games / heavy animations
- Deep native sensor / hardware access
- Apps requiring 60fps custom animations
- Bluetooth/NFC intensive apps (use plugins, but complex)

## Stack Options

| Option | UI Framework | Best For |
|--------|-------------|---------|
| Capacitor + Ionic | Ionic components | Full mobile-optimized UI |
| Capacitor + React | React + Tailwind | Web team reuse |
| Capacitor + Vue | Vue + Ionic | Vue teams |
| Capacitor + Angular | Angular + Ionic | Enterprise Angular teams |

## Project Structure (Capacitor + React)

```
src/
├── App.tsx
├── pages/                # Screen components
├── components/           # Shared UI components
├── hooks/                # Business logic hooks
├── services/             # API, storage services
└── store/                # State management
android/                  # Native Android project (generated)
├── app/src/main/
│   ├── AndroidManifest.xml
│   └── java/.../MainActivity.kt
capacitor.config.ts       # Capacitor configuration
```

## Capacitor Config

```typescript
// capacitor.config.ts
import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.example.app',
  appName: 'My App',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
  },
  android: {
    buildOptions: {
      releaseType: 'APK', // or AAB for Play Store
    },
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 0,
      backgroundColor: '#FFFFFF',
    },
    PushNotifications: {
      presentationOptions: ['badge', 'sound', 'alert'],
    },
  },
};
```

## Native Plugin Usage

```typescript
import { Camera, CameraResultType } from '@capacitor/camera';
import { SecureStorage } from '@aparajita/capacitor-secure-storage';
import { PushNotifications } from '@capacitor/push-notifications';
import { Geolocation } from '@capacitor/geolocation';

// Camera
const takePhoto = async () => {
  const photo = await Camera.getPhoto({
    quality: 90,
    allowEditing: false,
    resultType: CameraResultType.Uri,
  });
  return photo.webPath;
};

// Secure storage: do not store auth tokens in Capacitor Preferences.
// Use a platform-backed secure storage plugin such as
// @aparajita/capacitor-secure-storage, Ionic Identity Vault, or an
// equivalent Android Keystore-backed plugin.
const saveToken = async (token: string) => {
  await SecureStorage.set({ key: 'auth_token', value: token });
};

const getToken = async (): Promise<string | null> => {
  const { value } = await SecureStorage.get({ key: 'auth_token' });
  return value;
};

// Push notifications
const initPush = async () => {
  const permission = await PushNotifications.requestPermissions();
  if (permission.receive === 'granted') {
    await PushNotifications.register();
  }
  PushNotifications.addListener('registration', () => {
    console.log('Push registration succeeded');
  });
};
```

## Performance Best Practices

- Ensure hardware acceleration is enabled for the application in AndroidManifest.xml (default in Capacitor)
- Enable HTTP caching in Android WebView settings
- Lazy-load routes with React.lazy / dynamic imports
- Avoid `setTimeout`/`setInterval` for animations; use CSS transitions
- Use `@ionic/react` components — they handle mobile-specific touch handling
- Ionic virtual scroll for long lists

## Build & Deploy

```bash
# Build web assets
npm run build

# Sync to native
npx cap sync android

# Open in Android Studio
npx cap open android

# Build release APK/AAB via Android Studio or:
cd android && ./gradlew bundleRelease
```

## Custom Native Plugin (when built-in plugins don't cover it)

```kotlin
// android/app/src/main/java/.../MyPlugin.kt
@CapacitorPlugin(name = "MyPlugin")
class MyPlugin : Plugin() {
    @PluginMethod
    fun doNativeWork(call: PluginCall) {
        val value = call.getString("input") ?: return call.reject("No input")
        // Do native work
        val result = JSObject()
        result.put("output", "processed: $value")
        call.resolve(result)
    }
}

// TypeScript usage
import { registerPlugin } from '@capacitor/core';
const MyPlugin = registerPlugin<{ doNativeWork: (opts: { input: string }) => Promise<{ output: string }> }>('MyPlugin');
const result = await MyPlugin.doNativeWork({ input: 'hello' });
```


- Choose native Kotlin or Java for a fully native app and choose Flutter, React Native, or Kotlin Multiplatform only when the shared code benefit outweighs the platform cost.

- Keep one clear architecture for state, navigation, data, and background work and keep package names, permissions, and privacy disclosures matched to the actual project.

- Verify release critical library versions and store policy thresholds against current Android and library documentation before shipping.

## Build screens and flows

- Design each screen from the existing design system and keep touch targets, spacing, and text sizes consistent across screens.

- Handle errors with a visible state and a recovery action and never leave a user on a blank screen after a failure.

- Apply image orientation correctly in avatar and sent photo pipelines and keep keyboard behaviour correct at every text field.

- Optimise startup, memory, and rendering for low end devices and measure the result instead of claiming it from code inspection.

## Run XML journey tests

- Parse the journey XML in order and treat the actions block as the source of truth for the flow under test.

- Run interactive actions in sequence with ADB and tap the centre of each target element bounds after computing the midpoint from its bounds.

- Wait one to two seconds after an interactive action so layouts and transitions finish before the next assertion.

- Verify each state assertion by inspecting the current screen without interacting or scrolling and fail the assertion when any sub assertion is missing.

- Stop the journey at the first failure and mark later steps as skipped with the exact reason recorded.

- Write the outcome as a JSON report naming each action, its status, and the commands used and redact passwords, OTP codes, tokens, and payment data from that report.

## Ship a CI built release

- Ship a release only from continuous integration and never present a hand built local APK as an official release.

- Download the served release artifact and verify its package name, version code, signer, assets, and native libraries before delivery.

- Compare the release artifact against an artifact that runs on the same device when startup or packaging is in doubt.

- Treat a green build with no launch or smoke test as no evidence that the app opens.

## Alternatives for better results

- Use the pre delivery verification gate skill for measured geometry, fixed overlay ancestry, and control wiring checks before claiming a build is done.

- Use the evidence first crash diagnosis skill when an app still crashes on launch after a fix instead of building another fix on a guess.
