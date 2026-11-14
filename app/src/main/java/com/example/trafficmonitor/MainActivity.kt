package com.example.trafficmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.trafficmonitor.databinding.ActivityMainBinding
import com.example.trafficmonitor.databinding.DialogAppInfoBinding
import com.example.trafficmonitor.ui.GlassBottomBar
import com.example.trafficmonitor.ui.fadeSlideIn
import com.example.trafficmonitor.ui.onPressFeedback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private lateinit var adapterTop: AppUsageAdapter
    private lateinit var adapterAll: AppUsageAdapter

    private var refreshRequest = 0
    private var seriesRequest = 0
    private var summary: TrafficRepository.Summary? = null

    private var currentPeriod = TrafficSeriesRepository.Period.WEEK
    private val seriesCache = mutableMapOf<TrafficSeriesRepository.Period, TrafficSeriesRepository.Series>()

    private var limitDraftMb = 0L
    private var currentSection = 0
    private var dataLoadedOnce = false
    private var dotPulse: android.animation.ValueAnimator? = null

    private var appsSort = 0
    private var allApps: List<AppUsage> = emptyList()

    private val oneMb = 1024L * 1024L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        binding.tvVersion.text = getString(R.string.app_version_footer, appVersionName())

        setupSystemUi()
        setupInsets()
        setupTopBar()
        setupLists()
        setupChart()
        setupAppsSort()
        setupBottomBar()
        setupLimitControls()
        setupThemeControls()
        setupClicks()
        setupRefresh()

        updateSwitchFromPrefs()
        updateLimitDraftFromPrefs()
        updateAccessCard()

        showSection(0, animate = false)
        animateEntrance()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentSection != 0) {
                    binding.bottomBar.select(0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        updateAccessCard()
        updateSwitchFromPrefs()
        updateLimitDraftFromPrefs()

        // Если мониторинг был включён, после возвращения в приложение снова поднимаем сервис.
        if (prefs.monitoringEnabled) {
            TrafficMonitorService.start(this)
        }

        refreshData(showSkeleton = !dataLoadedOnce)
        loadSeries(currentPeriod)
    }

    // ==================================================================
    // Визуальный слой
    // ==================================================================

    private fun setupSystemUi() {
        val night = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        // До Android 8 нельзя сделать тёмные иконки навигации — добавляем
        // лёгкую затемняющую подложку, иначе они теряются на светлом фоне.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            window.navigationBarColor = ColorUtils.setAlphaComponent(Color.BLACK, 0x40)
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.updatePadding(top = bars.top + dp(14))
            binding.contentRoot.updatePadding(bottom = dp(104) + bars.bottom)
            (binding.bottomScrim.layoutParams as FrameLayout.LayoutParams).apply {
                height = dp(116) + bars.bottom
            }
            (binding.bottomBar.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                dp(16) + bars.bottom
            insets
        }
    }

    private fun setupTopBar() {
        val now = Calendar.getInstance()
        val locale = Locale("ru")
        binding.tvGreeting.text =
            SimpleDateFormat("EEEE, d MMMM", locale).format(now.time).uppercase(locale)
        binding.tvMonthLabel.text =
            SimpleDateFormat("LLLL yyyy", locale).format(now.time).uppercase(locale)
        binding.tvTitle.text = getString(R.string.nav_overview)
    }

    private fun setupLists() {
        adapterTop = AppUsageAdapter()
        adapterAll = AppUsageAdapter()

        listOf(binding.rvAppsTop, binding.rvAppsAll).forEach { recycler ->
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.setHasFixedSize(false)
            (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
        binding.rvAppsTop.adapter = adapterTop
        binding.rvAppsAll.adapter = adapterAll

        adapterTop.onAppClick = { showAppInfo(it) }
        adapterAll.onAppClick = { showAppInfo(it) }
    }

    private fun setupAppsSort() {
        binding.appsSortSwitch.setItems(
            listOf("Трафик ↓", "Трафик ↑", "А–Я"),
            selected = appsSort
        )
        binding.appsSortSwitch.setOnSelected { index ->
            appsSort = index
            refreshAppsList()
        }
    }

    /** Пересобирает оба списка с учётом выбранной сортировки. */
    private fun refreshAppsList() {
        if (summary == null) {
            adapterAll.setItems(emptyList())
            adapterTop.setItems(emptyList())
            return
        }

        val total = summary?.monthBytes ?: 0L
        val sorted = when (appsSort) {
            1 -> allApps.sortedBy { it.bytes }
            2 -> allApps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, AppUsage::label))
            else -> allApps.sortedByDescending { it.bytes }
        }
        adapterAll.setItems(sorted, total)
        adapterTop.setItems(allApps.sortedByDescending { it.bytes }.take(5), total)

        binding.tvAppsHint.text = "${allApps.size} приложений · за месяц"
        binding.tvEmpty.isVisible = allApps.isEmpty()
    }

    private fun setupChart() {
        binding.periodSwitch.setItems(
            listOf(
                getString(R.string.period_day),
                getString(R.string.period_week),
                getString(R.string.period_month)
            ),
            selected = 1
        )
        currentPeriod = TrafficSeriesRepository.Period.WEEK
        binding.periodSwitch.setOnSelected { index ->
            currentPeriod = when (index) {
                0 -> TrafficSeriesRepository.Period.DAY
                2 -> TrafficSeriesRepository.Period.MONTH
                else -> TrafficSeriesRepository.Period.WEEK
            }
            seriesCache[currentPeriod]?.let { renderSeries(currentPeriod, it, animate = true) }
                ?: loadSeries(currentPeriod)
        }
    }

    private fun setupBottomBar() {
        binding.bottomBar.setItems(
            listOf(
                GlassBottomBar.NavItem(R.drawable.ic_nav_overview, "Обзор", "Обзор"),
                GlassBottomBar.NavItem(R.drawable.ic_nav_apps, "Приложения", "Все приложения"),
                GlassBottomBar.NavItem(R.drawable.ic_nav_limits, "Лимиты", "Лимиты и настройки")
            ),
            selected = 0
        )
        binding.bottomBar.setOnSelected { showSection(it) }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            color(R.color.accent_cyan),
            color(R.color.accent_violet),
            color(R.color.accent_coral)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(color(R.color.surface))
        binding.swipeRefresh.setOnRefreshListener {
            refreshData(showSkeleton = false)
            loadSeries(currentPeriod, force = true)
        }
    }

    private fun setupClicks() {
        binding.btnAccess.onPressFeedback { showUsageAccessDialog() }
        binding.btnAllApps.onPressFeedback { binding.bottomBar.select(1) }
        binding.btnSaveLimit.onPressFeedback { saveLimit() }
    }

    private fun animateEntrance() {
        binding.sectionOverview.children.forEachIndexed { index, view ->
            view.fadeSlideIn(delay = index * 55L)
        }
    }

    private fun showSection(index: Int, animate: Boolean = true) {
        val sections = listOf(
            binding.sectionOverview,
            binding.sectionApps,
            binding.sectionLimits
        )
        if (index !in sections.indices) return
        currentSection = index

        sections.forEachIndexed { i, view ->
            if (i == index) {
                view.isVisible = true
                if (animate) {
                    view.alpha = 0f
                    view.translationY = dpF(12f)
                    view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(300L)
                        .start()
                }
            } else {
                view.isVisible = false
            }
        }

        binding.tvTitle.text = when (index) {
            1 -> getString(R.string.nav_apps)
            2 -> getString(R.string.nav_limits)
            else -> getString(R.string.nav_overview)
        }
        binding.tvGreeting.alpha = if (index == 0) 1f else 0.6f
        binding.scrollView.scrollTo(0, 0)
    }

    // ==================================================================
    // Лимит и мониторинг
    // ==================================================================

    private fun updateSwitchFromPrefs() {
        val switch = binding.switchMonitor
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = prefs.monitoringEnabled

        binding.tvMonitorSub.text = if (prefs.monitoringEnabled) {
            "Включён · проверка каждые 60 секунд"
        } else {
            "Выключен · уведомлений не будет"
        }

        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.monitoringEnabled = isChecked

            if (isChecked) {
                requestNotificationPermission()
                TrafficMonitorService.start(this)

                val message = if (UsageAccess.has(this)) {
                    "Мониторинг включён"
                } else {
                    "Мониторинг включён. Разрешите доступ к статистике"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } else {
                TrafficMonitorService.stop(this)
                Toast.makeText(this, "Мониторинг остановлен", Toast.LENGTH_SHORT).show()
            }
            updateSwitchFromPrefs()
        }
    }

    private fun updateLimitDraftFromPrefs() {
        limitDraftMb = prefs.limitMb.coerceIn(0L, MAX_LIMIT_MB)
        binding.sliderLimit.value = limitDraftMb.toFloat()
        updateLimitPreview()
        updatePresetChips()
    }

    private fun setupLimitControls() {
        binding.sliderLimit.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                limitDraftMb = value.roundToInt().toLong().coerceIn(0L, MAX_LIMIT_MB)
                updateLimitPreview()
                updatePresetChips()
            }
        }

        val presets = mapOf(
            binding.chipOff to 0L,
            binding.chip1 to 1024L,
            binding.chip3 to 3 * 1024L,
            binding.chip5 to 5 * 1024L,
            binding.chip10 to 10 * 1024L,
            binding.chip20 to 20 * 1024L
        )
        presets.forEach { (chip, value) ->
            chip.onPressFeedback {
                limitDraftMb = value
                binding.sliderLimit.value = value.toFloat()
                updateLimitPreview()
                updatePresetChips()
            }
        }
    }

    private fun updatePresetChips() {
        val presets = mapOf(
            binding.chipOff to 0L,
            binding.chip1 to 1024L,
            binding.chip3 to 3 * 1024L,
            binding.chip5 to 5 * 1024L,
            binding.chip10 to 10 * 1024L,
            binding.chip20 to 20 * 1024L
        )
        presets.forEach { (chip, value) ->
            chip.isSelected = value == limitDraftMb
        }
    }

    private fun updateLimitPreview() {
        if (limitDraftMb <= 0) {
            binding.tvLimitBig.text = "Безлимит"
            binding.tvLimitHint.text = "Безлимитный тариф — предупреждения и ограничения отключены"
            return
        }

        val limitBytes = limitDraftMb * oneMb
        binding.tvLimitBig.text = Format.size(limitBytes)

        val used = summary?.monthBytes ?: 0L
        binding.tvLimitHint.text = when {
            used > limitBytes -> "Лимит уже превышен на ${Format.size(used - limitBytes)}"
            limitDraftMb == prefs.limitMb -> "Осталось ${Format.size(limitBytes - used)} из ${Format.size(limitBytes)}"
            else -> "Будет сохранено: ${Format.size(limitBytes)} на месяц. Сейчас израсходовано ${Format.size(used)}"
        }
    }

    private fun saveLimit() {
        prefs.limitMb = limitDraftMb
        prefs.clearWarnings()

        val message = if (limitDraftMb > 0) {
            "Лимит сохранён: $limitDraftMb МБ"
        } else {
            "Безлимит включён"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // Пусть сервис сразу обновит уведомление, не дожидаясь следующей минуты.
        if (prefs.monitoringEnabled) {
            TrafficMonitorService.start(this)
        }

        summary?.let { updateHero(it) }
        updateLimitPreview()
        refreshData(showSkeleton = false)
    }

    // ==================================================================
    // Тема
    // ==================================================================

    private fun setupThemeControls() {
        binding.themeSystem.onPressFeedback { applyTheme(Prefs.THEME_SYSTEM) }
        binding.themeLight.onPressFeedback { applyTheme(Prefs.THEME_LIGHT) }
        binding.themeDark.onPressFeedback { applyTheme(Prefs.THEME_DARK) }
        binding.tvPrivacy.setOnClickListener { openPrivacyPolicy() }
        updateThemeChips()
    }

    /** Версия приложения для подписи внизу экрана «Лимиты». */
    @Suppress("DEPRECATION")
    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    /** Политика конфиденциальности — требование RuStore (ФЗ-152). */
    private fun openPrivacyPolicy() {
        val url = "https://quartzglow.github.io/traffic-monitor/privacy.html"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateThemeChips() {
        val mode = prefs.themeMode
        binding.themeSystem.isSelected = mode == Prefs.THEME_SYSTEM
        binding.themeLight.isSelected = mode == Prefs.THEME_LIGHT
        binding.themeDark.isSelected = mode == Prefs.THEME_DARK
    }

    private fun applyTheme(mode: Int) {
        if (prefs.themeMode == mode) return
        prefs.themeMode = mode
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    // ==================================================================
    // Данные
    // ==================================================================

    private fun updateAccessCard() {
        binding.cardAccess.isVisible = !UsageAccess.has(this)
    }

    private fun refreshData(showSkeleton: Boolean) {
        if (!UsageAccess.has(this)) {
            refreshRequest++
            summary = null
            binding.skeletonGroup.isVisible = false
            binding.swipeRefresh.isRefreshing = false
            adapterTop.setItems(emptyList())
            adapterAll.setItems(emptyList())
            showNoAccessState()
            return
        }

        val requestId = ++refreshRequest
        if (showSkeleton && summary == null) {
            binding.skeletonGroup.isVisible = true
            binding.rvAppsTop.isVisible = false
            binding.tvEmpty.isVisible = false
        }

        // Запрос к системе может быть небыстрым, поэтому делаем его в фоне.
        thread {
            val result = runCatching { TrafficRepository.load(this) }

            runOnUiThread {
                if (requestId != refreshRequest || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                result.onSuccess { data ->
                    showSummary(data)
                }.onFailure { error ->
                    Log.e(TAG, "Ошибка чтения статистики", error)
                    binding.skeletonGroup.isVisible = false
                    binding.swipeRefresh.isRefreshing = false
                    showErrorState(error)
                }
            }
        }
    }

    private fun showNoAccessState() {
        allApps = emptyList()
        binding.tvTotal.text = "—"
        binding.tvUnit.text = ""
        binding.tvTotal.setTextColor(color(R.color.text_primary))
        binding.tvOfLimit.text = "нет доступа к статистике"
        binding.tvToday.text = "Сегодня: —"
        binding.tvLeftValue.text = "—"
        binding.tvLimitValue.text = "—"
        binding.tvForecast.text = "—"
        binding.tvAllTotal.text = "—"
        binding.tvAllCount.text = "—"
        binding.tvAppsHint.text = "нет доступа к данным"
        binding.ringView.setProgressImmediate(0f)
        binding.ringView.pulse = false
        setStatusChip("Нет доступа", color(R.color.accent_coral), pulse = false)
        binding.tvEmpty.isVisible = true
        binding.tvEmpty.text = "Без специального доступа Android не показывает расход трафика по приложениям."
        binding.rvAppsTop.isVisible = true
        binding.chartView.setData(emptyList(), emptyList(), animate = false)
        binding.tvChartTotal.text = "—"
    }

    private fun showErrorState(error: Throwable) {
        allApps = emptyList()
        binding.tvTotal.text = "—"
        binding.tvUnit.text = ""
        binding.tvOfLimit.text = "не удалось прочитать статистику"
        binding.tvToday.text = "Сегодня: —"
        binding.tvEmpty.isVisible = true
        binding.tvEmpty.text = "Ошибка: ${error.message ?: "неизвестная ошибка"}"
        binding.rvAppsTop.isVisible = true
        adapterTop.setItems(emptyList())
        adapterAll.setItems(emptyList())
        binding.tvAllTotal.text = "—"
        binding.tvAllCount.text = "—"
        Toast.makeText(this, "Не удалось прочитать статистику", Toast.LENGTH_SHORT).show()
    }

    private fun showSummary(data: TrafficRepository.Summary) {
        summary = data
        dataLoadedOnce = true
        binding.skeletonGroup.isVisible = false
        binding.swipeRefresh.isRefreshing = false

        allApps = data.apps
        refreshAppsList()
        binding.rvAppsTop.isVisible = true

        binding.tvEmpty.isVisible = data.apps.isEmpty()
        binding.tvEmpty.text = "За этот месяц ещё нет данных о мобильном трафике."

        binding.tvToday.text = "Сегодня: ${Format.size(data.todayBytes)}"
        binding.tvAllTotal.text = Format.size(data.monthBytes)
        binding.tvAllCount.text = data.apps.size.toString()

        updateHero(data)
        updateLimitPreview()
    }

    private fun updateHero(data: TrafficRepository.Summary) {
        setTotalValue(data.monthBytes, animate = dataLoadedOnce)

        val limitMb = prefs.limitMb
        val month = data.monthBytes

        if (limitMb <= 0) {
            binding.tvOfLimit.text = "безлимитный тариф"
            binding.tvLimitValue.text = "∞"
            binding.tvLeftValue.text = "∞"
            binding.ringView.startColor = color(R.color.accent_violet)
            binding.ringView.endColor = color(R.color.accent_cyan)
            binding.ringView.animateToProgress(0f, 500L)
            binding.ringView.pulse = false
            binding.tvTotal.setTextColor(color(R.color.text_primary))
            setStatusChip("Безлимит", color(R.color.accent_violet))
        } else {
            val limitBytes = limitMb * oneMb
            val ratio = (month.toDouble() / limitBytes).coerceIn(0.0, 1.0)
            val percent = (month * 100.0 / limitBytes).toInt().coerceAtLeast(0)

            binding.ringView.animateToProgress(ratio.toFloat(), 950L)
            binding.ringView.pulse = percent >= 80
            binding.tvOfLimit.text = "из ${Format.size(limitBytes)}"
            binding.tvLimitValue.text = Format.size(limitBytes)
            binding.tvLeftValue.text = Format.size((limitBytes - month).coerceAtLeast(0L))

            when {
                month >= limitBytes -> {
                    binding.ringView.startColor = color(R.color.accent_coral)
                    binding.ringView.endColor = color(R.color.accent_violet)
                    binding.tvTotal.setTextColor(color(R.color.accent_coral))
                    binding.tvOfLimit.text = "превышен на ${Format.size(month - limitBytes)}"
                    setStatusChip("Лимит превышен", color(R.color.accent_coral), pulse = true)
                }
                percent >= 80 -> {
                    binding.ringView.startColor = color(R.color.accent_amber)
                    binding.ringView.endColor = color(R.color.accent_coral)
                    binding.tvTotal.setTextColor(color(R.color.text_primary))
                    setStatusChip("$percent% лимита", color(R.color.accent_amber), pulse = true)
                }
                else -> {
                    binding.ringView.startColor = color(R.color.accent_teal)
                    binding.ringView.endColor = color(R.color.accent_violet)
                    binding.tvTotal.setTextColor(color(R.color.text_primary))
                    setStatusChip("В норме", color(R.color.accent_teal))
                }
            }
        }

        val forecast = forecastBytes(month)
        binding.tvForecast.text = if (month > 0) Format.size(forecast) else "—"
    }

    private fun forecastBytes(monthBytes: Long): Long {
        val calendar = Calendar.getInstance()
        val daysPassed = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (daysPassed <= 0) return 0L
        return (monthBytes.toDouble() / daysPassed * daysInMonth).toLong()
    }

    private fun setTotalValue(bytes: Long, animate: Boolean) {
        val unit = if (bytes >= 1024L * oneMb) "ГБ" else "МБ"
        binding.tvUnit.text = unit
        val formatter: (Long) -> String = { value ->
            if (unit == "ГБ") {
                String.format(Locale.getDefault(), "%.2f", value / 1024.0 / 1024.0 / 1024.0)
            } else {
                String.format(Locale.getDefault(), "%.1f", value / 1024.0 / 1024.0)
            }
        }
        binding.tvTotal.setFormatter(formatter)
        if (animate) {
            binding.tvTotal.animateToValue(bytes)
        } else {
            binding.tvTotal.setValueImmediate(bytes)
        }
    }

    private fun setStatusChip(text: String, accent: Int, pulse: Boolean = false) {
        binding.tvStatus.text = text
        binding.tvStatus.setTextColor(accent)

        binding.chipStatus.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(22f)
            setColor(ColorUtils.setAlphaComponent(accent, 34))
            setStroke(dp(1), ColorUtils.setAlphaComponent(accent, 110))
        }

        binding.dotStatus.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent)
        }

        binding.dotStatus.animate().cancel()
        dotPulse?.cancel()
        if (pulse) {
            dotPulse = android.animation.ValueAnimator.ofFloat(1f, 0.3f).apply {
                duration = 750L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                addUpdateListener { binding.dotStatus.alpha = it.animatedValue as Float }
                start()
            }
        } else {
            binding.dotStatus.alpha = 1f
        }
    }

    // ------------------------------------------------------------------
    // График
    // ------------------------------------------------------------------

    private fun loadSeries(period: TrafficSeriesRepository.Period, force: Boolean = false) {
        val cached = seriesCache[period]
        if (cached != null && !force) {
            renderSeries(period, cached, animate = false)
            return
        }

        val requestId = ++seriesRequest
        thread {
            val series = TrafficSeriesRepository.load(this, period)
            runOnUiThread {
                if (requestId != seriesRequest || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                seriesCache[period] = series
                if (period == currentPeriod) {
                    renderSeries(period, series, animate = true)
                }
            }
        }
    }

    private fun renderSeries(
        period: TrafficSeriesRepository.Period,
        series: TrafficSeriesRepository.Series,
        animate: Boolean
    ) {
        binding.chartView.startColor = color(R.color.accent_cyan)
        binding.chartView.endColor = color(R.color.accent_violet)
        binding.chartView.setData(series.values, series.labels, animate)

        binding.tvChartTotal.text = Format.size(series.total)
        binding.tvChartCaption.text = when (period) {
            TrafficSeriesRepository.Period.DAY -> "по часам · сегодня"
            TrafficSeriesRepository.Period.WEEK -> "за последние 7 дней"
            TrafficSeriesRepository.Period.MONTH -> "за текущий месяц"
        }
    }

    // ------------------------------------------------------------------
    // Карточка приложения
    // ------------------------------------------------------------------

    private fun showAppInfo(app: AppUsage) {
        val dialogBinding = DialogAppInfoBinding.inflate(layoutInflater)

        dialogBinding.ivDialogIcon.setImageDrawable(
            app.icon ?: ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon)
        )
        dialogBinding.tvDialogName.text = app.label
        dialogBinding.tvDialogPackage.text = app.packageName
        dialogBinding.tvDialogUsage.text = Format.size(app.bytes)

        val total = summary?.monthBytes ?: 0L
        val percent = if (total > 0) {
            ((app.bytes * 100.0) / total).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        dialogBinding.tvDialogPercent.text = "$percent%"
        dialogBinding.pbDialogUsage.progress = percent

        val flags = runCatching {
            packageManager.getApplicationInfo(app.packageName, 0).flags
        }.getOrDefault(0)
        val kind = if (flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            "Системное приложение"
        } else {
            "Установленное приложение"
        }
        dialogBinding.tvDialogMeta.text = "$kind · UID ${app.uid}"

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Настройки") { _, _ -> openAppSettings(app.packageName) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun openAppSettings(pkg: String) {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================================================================
    // Разрешения и системные экраны
    // ==================================================================

    private fun showUsageAccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Доступ к статистике")
            .setMessage(
                "Android не показывает статистику трафика без специального разрешения.\n\n" +
                    "В открывшемся списке найдите «Монитор трафика» и включите доступ."
            )
            .setPositiveButton("Открыть настройки") { _, _ ->
                openUsageAccessSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e2: Exception) {
                Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_NOTIFICATIONS &&
            grantResults.isNotEmpty() &&
            grantResults[0] != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Без разрешения на уведомления сервис будет работать незаметно",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ------------------------------------------------------------------
    // Мелочи
    // ------------------------------------------------------------------

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun dpF(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATIONS = 101
        private const val MAX_LIMIT_MB = 20L * 1024L
    }
}
