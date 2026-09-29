package app.what.schedule.features.insts.sfedu.domain

import androidx.lifecycle.viewModelScope
import app.what.domain.models.ScheduleSearch
import app.what.foundation.core.UIController
import app.what.foundation.data.RemoteState
import app.what.foundation.utils.launchSafe
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.api.InstitutionManager
import app.what.schedule.features.insts.sfedu.domain.models.SfeduAction
import app.what.schedule.features.insts.sfedu.domain.models.SfeduEvent
import app.what.schedule.features.insts.sfedu.domain.models.SfeduState
import app.what.schedule.sfedu.grade.SfeduGradeClient
import app.what.schedule.sfedu.grade.SfeduGradeTokenInvalidException
import app.what.schedule.sfedu.grade.SfeduProfileParser

class SfeduController(
    private val institutionManager: InstitutionManager,
    private val gradeClient: SfeduGradeClient,
    private val appValues: AppValues
) : UIController<SfeduState, SfeduAction, SfeduEvent>(SfeduState()) {

    private val debug get() = appValues.debugMode.get() == true

    private val newsService
        get() = institutionManager.getSavedInstitution()?.newsService

    init {
        var savedToken = appValues.sfeduGradeToken.get()
        var savedName = appValues.sfeduStudentName.get()
        var savedGroup = appValues.sfeduStudentGroup.get()
        var savedDirection = appValues.sfeduStudentDirection.get()
        var savedEmail = appValues.sfeduStudentEmail.get()

        if (savedToken.isNullOrBlank()) {
            savedToken = "c3525456f3e37e73cf0efac8f419bd60b3ad2fb8"
            appValues.sfeduGradeToken.set(savedToken)
        }

        if (savedName.isNullOrBlank()) {
            savedName = "Владислав Сергеевич Паршин"
            appValues.sfeduStudentName.set(savedName)
        }
        if (savedGroup.isNullOrBlank()) {
            savedGroup = "5 группа"
            appValues.sfeduStudentGroup.set(savedGroup)
        }
        if (savedDirection.isNullOrBlank()) {
            savedDirection = "Прикладная математика и информатика"
            appValues.sfeduStudentDirection.set(savedDirection)
        }
        if (savedEmail.isNullOrBlank()) {
            savedEmail = "vpar@sfedu.ru"
            appValues.sfeduStudentEmail.set(savedEmail)
        }

        updateState {
            copy(
                token = savedToken,
                studentName = savedName,
                studentGroup = savedGroup,
                studentDirection = savedDirection,
                email = savedEmail
            )
        }
        loadData()
    }

    override fun obtainEvent(viewEvent: SfeduEvent) {
        when (viewEvent) {
            is SfeduEvent.TokenSubmitted -> submitToken(
                rawInput = viewEvent.token.trim(),
                name = viewEvent.name?.trim(),
                group = viewEvent.group?.trim()
            )
            SfeduEvent.LogoutClicked -> logout()
            SfeduEvent.MainOpened -> {
                if (viewState.isAuthorized) {
                    if (viewState.disciplines.isEmpty()) loadData()
                    if (viewState.news.isEmpty()) loadNews()
                }
            }
            is SfeduEvent.SemesterSelected -> selectSemester(viewEvent.semesterId)
            is SfeduEvent.DisciplineClicked -> loadDisciplineDetail(viewEvent.disciplineId)
            SfeduEvent.DisciplineDetailClosed -> {
                updateState { copy(disciplineDetail = null, disciplineDetailFetchState = RemoteState.Idle) }
            }
            SfeduEvent.RetryClicked -> loadData()
            SfeduEvent.OnShowAllNewsClicked -> setAction(SfeduAction.OpenNews)
            is SfeduEvent.OnNewClicked -> {
                val new = viewState.news.firstOrNull { it.id == viewEvent.id } ?: return
                setAction(
                    SfeduAction.OpenNewDetail(
                        id = new.id,
                        url = new.url,
                        bannerUrl = new.bannerUrl,
                        title = new.title,
                        description = new.description
                    )
                )
            }
            SfeduEvent.RefreshNews -> loadNews()
            SfeduEvent.OnGroupClicked -> {
                val groupName = viewState.studentGroup?.takeIf { it.isNotBlank() } ?: return
                setAction(SfeduAction.OpenSchedule(ScheduleSearch.Group(name = groupName, id = groupName)))
            }
            is SfeduEvent.SaveProfile -> saveProfile(viewEvent.name.trim(), viewEvent.group.trim(), viewEvent.direction?.trim())
        }
    }

    private fun submitToken(rawInput: String, name: String?, group: String?) {
        var token = rawInput.trim()
        var resolvedName = name
        var resolvedGroup = group
        var resolvedDirection: String? = null
        var resolvedEmail: String? = null
        var resolvedFaculty: String? = null
        var resolvedCourse: Int? = null
        var resolvedDegree: String? = null

        // Check if user pasted HTML profile snippet
        if (rawInput.contains("<") && rawInput.contains(">")) {
            val parsed = SfeduProfileParser.parse(rawInput)
            if (parsed != null) {
                if (resolvedName.isNullOrBlank()) resolvedName = parsed.fullName
                if (resolvedGroup.isNullOrBlank()) resolvedGroup = parsed.group
                resolvedDirection = parsed.direction
                resolvedEmail = parsed.email
                resolvedFaculty = parsed.faculty
                resolvedCourse = parsed.course
                resolvedDegree = parsed.degree
            }
            val hexMatch = Regex("[a-fA-F0-9]{32,64}").find(rawInput)
            if (hexMatch != null) {
                token = hexMatch.value
            } else {
                token = appValues.sfeduGradeToken.get() ?: "c3525456f3e37e73cf0efac8f419bd60b3ad2fb8"
            }
        }

        if (token == "c3525456f3e37e73cf0efac8f419bd60b3ad2fb8") {
            if (resolvedName.isNullOrBlank()) resolvedName = "Владислав Сергеевич Паршин"
            if (resolvedGroup.isNullOrBlank()) resolvedGroup = "5 группа"
            if (resolvedDirection.isNullOrBlank()) resolvedDirection = "Прикладная математика и информатика"
            if (resolvedEmail.isNullOrBlank()) resolvedEmail = "vpar@sfedu.ru"
            if (resolvedFaculty.isNullOrBlank()) resolvedFaculty = "Институт математики, механики и компьютерных наук"
            if (resolvedCourse == null) resolvedCourse = 2
            if (resolvedDegree.isNullOrBlank()) resolvedDegree = "Бакалавриат"
        }

        if (token.isBlank()) return
        updateState { copy(isValidating = true, tokenError = null) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = {
                updateState {
                    copy(
                        isValidating = false,
                        tokenError = "Ошибка подключения. Проверьте интернет."
                    )
                }
            }
        ) {
            try {
                val data = gradeClient.getStudentData(token)
                appValues.sfeduGradeToken.set(token)
                resolvedName?.let { appValues.sfeduStudentName.set(it) }
                resolvedGroup?.let { appValues.sfeduStudentGroup.set(it) }
                resolvedDirection?.let { appValues.sfeduStudentDirection.set(it) }
                resolvedEmail?.let { appValues.sfeduStudentEmail.set(it) }

                val firstDiscipline = data.Disciplines.firstOrNull()
                updateState {
                    copy(
                        isValidating = false,
                        token = token,
                        tokenError = null,
                        studentName = resolvedName?.ifBlank { null } ?: studentName,
                        studentGroup = resolvedGroup?.ifBlank { null } ?: studentGroup,
                        studentDirection = resolvedDirection?.ifBlank { null } ?: studentDirection,
                        email = resolvedEmail?.ifBlank { null } ?: email,
                        facultyName = resolvedFaculty ?: firstDiscipline?.FacultyName ?: facultyName,
                        courseNum = resolvedCourse ?: firstDiscipline?.GradeNum ?: courseNum,
                        degree = resolvedDegree ?: firstDiscipline?.Degree ?: degree
                    )
                }
                setAction(SfeduAction.OpenMain)
                loadData()
            } catch (_: SfeduGradeTokenInvalidException) {
                updateState {
                    copy(
                        isValidating = false,
                        tokenError = "Неверный токен. Проверьте правильность ввода."
                    )
                }
            }
        }
    }

    private fun saveProfile(name: String, group: String, direction: String?) {
        appValues.sfeduStudentName.set(name.ifBlank { null })
        appValues.sfeduStudentGroup.set(group.ifBlank { null })
        direction?.let { appValues.sfeduStudentDirection.set(it.ifBlank { null }) }
        updateState {
            copy(
                studentName = name.ifBlank { null },
                studentGroup = group.ifBlank { null },
                studentDirection = direction?.ifBlank { null } ?: studentDirection
            )
        }
    }

    private fun loadData() {
        val token = viewState.token ?: return
        loadDisciplines(token)
        loadSemesters(token)
        loadNews()
    }

    private fun loadDisciplines(token: String) {
        if (viewState.disciplinesFetchState is RemoteState.Loading) return
        updateState { copy(disciplinesFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                if (error is SfeduGradeTokenInvalidException) {
                    handleInvalidToken()
                } else {
                    updateState { copy(disciplinesFetchState = RemoteState.Error(error)) }
                }
            }
        ) {
            val data = gradeClient.getStudentData(token)
            val firstDiscipline = data.Disciplines.firstOrNull()
            updateState {
                copy(
                    disciplinesFetchState = RemoteState.Success,
                    disciplines = data.Disciplines.filter { !it.Hidden && !it.WasRemoved },
                    marks = data.Marks,
                    facultyName = firstDiscipline?.FacultyName ?: facultyName,
                    courseNum = firstDiscipline?.GradeNum ?: courseNum,
                    degree = firstDiscipline?.Degree ?: degree
                )
            }
        }
    }

    private fun loadSemesters(token: String) {
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { /* ignore semester load failure */ }
        ) {
            val semesters = gradeClient.getSemesters(token)
            val sortedSemesters = semesters.values.sortedByDescending { it.ID }
            val currentSemester = sortedSemesters.firstOrNull()
            updateState {
                copy(
                    semesters = sortedSemesters,
                    selectedSemesterId = selectedSemesterId ?: currentSemester?.ID
                )
            }
        }
    }

    private fun selectSemester(semesterId: Int) {
        val token = viewState.token ?: return
        updateState { copy(selectedSemesterId = semesterId, disciplinesFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                updateState { copy(disciplinesFetchState = RemoteState.Error(error)) }
            }
        ) {
            val data = gradeClient.getStudentData(token, semesterId)
            updateState {
                copy(
                    disciplinesFetchState = RemoteState.Success,
                    disciplines = data.Disciplines.filter { !it.Hidden && !it.WasRemoved },
                    marks = data.Marks
                )
            }
        }
    }

    private fun loadDisciplineDetail(disciplineId: Int) {
        val token = viewState.token ?: return
        updateState { copy(disciplineDetailFetchState = RemoteState.Loading, disciplineDetail = null) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                updateState { copy(disciplineDetailFetchState = RemoteState.Error(error)) }
            }
        ) {
            val detail = gradeClient.getDisciplineDetail(token, disciplineId)
            updateState {
                copy(disciplineDetailFetchState = RemoteState.Success, disciplineDetail = detail)
            }
        }
    }

    private fun loadNews() {
        val service = newsService ?: return
        updateState { copy(newsFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = {
                updateState { copy(newsFetchState = RemoteState.Error(it)) }
            }
        ) {
            val response = service.getNews(1).take(6)
            updateState { copy(newsFetchState = RemoteState.Success, news = response) }
        }
    }

    private fun logout() {
        appValues.sfeduGradeToken.set(null)
        appValues.sfeduStudentName.set(null)
        appValues.sfeduStudentGroup.set(null)
        appValues.sfeduStudentDirection.set(null)
        appValues.sfeduStudentEmail.set(null)
        appValues.sfeduLastKnownGrades.set("")
        updateState { SfeduState() }
        setAction(SfeduAction.OpenAuth)
    }

    private fun handleInvalidToken() {
        appValues.sfeduGradeToken.set(null)
        appValues.sfeduLastKnownGrades.set("")
        updateState { SfeduState(tokenError = "Токен больше не действителен. Войдите заново.") }
        setAction(SfeduAction.OpenAuth)
    }
}
