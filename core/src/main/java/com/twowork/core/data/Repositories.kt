package com.twowork.core.data

import com.twowork.core.model.*
import com.twowork.core.net.ApiResult
import com.twowork.core.net.TwoWorkApi
import com.twowork.core.net.safeApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/** Public discovery + metrics. */
class DiscoveryRepository(private val api: TwoWorkApi) {
    suspend fun projects(
        q: String? = null, skills: String? = null, projectType: String? = null,
        experienceLevel: String? = null, minBudget: String? = null, maxBudget: String? = null,
        sort: String? = null, page: Int = 1
    ): ApiResult<ProjectListResponse> =
        safeApi { api.projects(q?.ifBlank { null }, skills?.ifBlank { null }, projectType?.ifBlank { null },
            experienceLevel?.ifBlank { null }, minBudget?.ifBlank { null }, maxBudget?.ifBlank { null }, sort?.ifBlank { null }, page) }

    suspend fun freelancers(q: String? = null, skills: String? = null, sort: String? = null, page: Int = 1): ApiResult<FreelancerListResponse> =
        safeApi { api.freelancers(q?.ifBlank { null }, skills?.ifBlank { null }, sort?.ifBlank { null }, page) }

    suspend fun matching(): ApiResult<MatchingResponse> = safeApi { api.matching() }
    suspend fun metrics(): ApiResult<MetricsResponse> = safeApi { api.metrics() }
}

/** Project hosting + proposals (client) and proposal submission (freelancer). */
class ProjectRepository(private val api: TwoWorkApi) {
    suspend fun create(body: ProjectRequest): ApiResult<CreatedProjectResponse> = safeApi { api.createProject(body) }
    suspend fun mine(): ApiResult<MyProjectsResponse> = safeApi { api.myProjects() }
    suspend fun publish(id: String): ApiResult<Unit> = safeApi { api.publishProject(id); Unit }
    suspend fun cancel(id: String): ApiResult<Unit> = safeApi { api.cancelProject(id); Unit }
    suspend fun proposals(id: String): ApiResult<ProposalsResponse> = safeApi { api.projectProposals(id) }
    suspend fun sendProposal(id: String, body: ProposalRequest): ApiResult<Unit> = safeApi { api.sendProposal(id, body); Unit }
    suspend fun accept(proposalId: String, body: AcceptRequest): ApiResult<Unit> = safeApi { api.acceptProposal(proposalId, body); Unit }
    suspend fun invite(projectId: String, body: InviteRequest): ApiResult<Unit> = safeApi { api.invite(projectId, body); Unit }
    suspend fun save(id: String): ApiResult<SavedResponse> = safeApi { api.saveProject(id) }
    suspend fun unsave(id: String): ApiResult<SavedResponse> = safeApi { api.unsaveProject(id) }
    suspend fun saved(): ApiResult<ProjectListResponse> = safeApi { api.savedProjects() }
}

/** Contracts, milestones, deliverables, disputes, ratings, funding. */
class ContractRepository(private val api: TwoWorkApi) {
    suspend fun contracts(): ApiResult<ContractsResponse> = safeApi { api.contracts() }
    suspend fun fund(milestoneId: String): ApiResult<FundingIntentResponse> = safeApi { api.createFundingIntent(milestoneId) }
    suspend fun deliver(milestoneId: String, body: DeliverableRequest): ApiResult<Unit> = safeApi { api.submitDeliverable(milestoneId, body); Unit }
    suspend fun requestRevision(deliverableId: String, body: FeedbackRequest): ApiResult<Unit> = safeApi { api.requestRevision(deliverableId, body); Unit }
    suspend fun acceptDelivery(deliverableId: String): ApiResult<Unit> = safeApi { api.acceptDeliverable(deliverableId); Unit }
    suspend fun dispute(milestoneId: String, body: DisputeRequest): ApiResult<Unit> = safeApi { api.openDispute(milestoneId, body); Unit }
    suspend fun rateFreelancer(milestoneId: String, body: RatingRequest): ApiResult<Unit> = safeApi { api.rateFreelancer(milestoneId, body); Unit }
    suspend fun rateClient(milestoneId: String, body: RatingRequest): ApiResult<Unit> = safeApi { api.rateClient(milestoneId, body); Unit }
    suspend fun cancel(contractId: String, reason: String, score: Int): ApiResult<Unit> =
        safeApi { api.cancelContract(contractId, CancelContractRequest(reason, score)); Unit }
}

/** Conversations / messaging + attachments. */
class MessageRepository(private val api: TwoWorkApi) {
    suspend fun conversations(): ApiResult<ConversationsResponse> = safeApi { api.conversations() }
    suspend fun messages(threadId: String, after: String? = null): ApiResult<MessagesResponse> =
        safeApi { api.messages(threadId, after) }
    suspend fun send(threadId: String, text: String, attachmentIds: List<String> = emptyList()): ApiResult<Unit> =
        safeApi { api.sendMessage(threadId, MessageRequest(message = text, attachmentIds = attachmentIds)); Unit }
    suspend fun openConversation(proposalId: String): ApiResult<String> =
        safeApi { api.openConversation(proposalId).thread.id }
    suspend fun uploadAttachment(bytes: ByteArray, mime: String, fileName: String): ApiResult<Attachment> =
        safeApi { api.uploadAttachment(fileName, bytes.toRequestBody(mime.toMediaTypeOrNull())).attachment }
    suspend fun downloadAttachment(id: String): ApiResult<ByteArray> =
        safeApi { api.downloadAttachment(id).bytes() }
}

/** Notifications + invitations + reports. */
class EngagementRepository(private val api: TwoWorkApi) {
    suspend fun notifications(): ApiResult<NotificationFeed> = safeApi { api.notifications() }
    suspend fun markRead(id: String? = null): ApiResult<UnreadResponse> = safeApi { api.markNotificationsRead(NotificationReadRequest(id)) }
    suspend fun invitations(): ApiResult<InvitationsResponse> = safeApi { api.invitations() }
    suspend fun report(body: ReportRequest): ApiResult<Unit> = safeApi { api.report(body); Unit }
}

/** Profile + verification + dashboard. */
class ProfileRepository(private val api: TwoWorkApi) {
    suspend fun profile(): ApiResult<ProfileResponse> = safeApi { api.profile() }
    suspend fun dashboard(): ApiResult<DashboardResponse> = safeApi { api.dashboard() }
    suspend fun saveClient(body: ClientProfileRequest): ApiResult<Unit> = safeApi { api.saveClientProfile(body); Unit }
    suspend fun saveFreelancer(body: FreelancerProfileRequest): ApiResult<Unit> = safeApi { api.saveFreelancerProfile(body); Unit }
    suspend fun submitVerification(body: VerificationSubmitRequest): ApiResult<Unit> = safeApi { api.submitVerification(body); Unit }
    suspend fun uploadDocument(type: String, bytes: ByteArray, mime: String): ApiResult<Unit> = safeApi {
        api.uploadVerificationDocument(type, bytes.toRequestBody(mime.toMediaTypeOrNull())); Unit
    }
    suspend fun uploadMedia(kind: String, caption: String, bytes: ByteArray, mime: String): ApiResult<ProfileMedia> = safeApi {
        api.uploadProfileMedia(kind, caption, bytes.toRequestBody(mime.toMediaTypeOrNull())).media
    }
    suspend fun deleteMedia(id: String): ApiResult<Unit> = safeApi { api.deleteProfileMedia(id); Unit }
}

/** Wallet, plans/subscription, apply quota, bank account and settlements. */
class WalletRepository(private val api: TwoWorkApi) {
    suspend fun wallet(): ApiResult<WalletResponse> = safeApi { api.wallet() }
    suspend fun topup(amount: String): ApiResult<Unit> = safeApi { api.topupWallet(TopupRequest(amount)); Unit }
    suspend fun subscription(): ApiResult<SubscriptionResponse> = safeApi { api.subscription() }
    suspend fun subscribe(plan: String): ApiResult<Unit> = safeApi { api.subscribe(SubscribeRequest(plan)); Unit }
    suspend fun quota(): ApiResult<QuotaResponse> = safeApi { api.quota() }
    suspend fun bankAccount(): ApiResult<BankAccountResponse> = safeApi { api.bankAccount() }
    suspend fun saveBank(body: BankAccountRequest): ApiResult<Unit> = safeApi { api.saveBankAccount(body); Unit }
    suspend fun settlements(): ApiResult<SettlementsResponse> = safeApi { api.settlements() }
    suspend fun requestSettlement(amount: String): ApiResult<Unit> = safeApi { api.requestSettlement(SettlementRequest(amount)); Unit }
    suspend fun affiliate(): ApiResult<AffiliateResponse> = safeApi { api.affiliate() }
}

/** Skill assessments: available exams, start an attempt, fetch questions, submit. */
class AssessmentRepository(private val api: TwoWorkApi) {
    suspend fun available(): ApiResult<AssessmentsResponse> = safeApi { api.assessmentsAvailable() }
    suspend fun start(skill: String, level: Int): ApiResult<StartAttemptResponse> = safeApi { api.startAssessment(skill, level) }
    suspend fun questions(attemptId: String): ApiResult<AttemptQuestionsResponse> = safeApi { api.attemptQuestions(attemptId) }
    suspend fun submit(attemptId: String, answers: Map<String, String>): ApiResult<SubmitResultResponse> =
        safeApi { api.submitAttempt(attemptId, SubmitAnswersRequest(answers)) }
}

/** Self-hosted in-app update manifest (app/android-latest.json on the server). */
class AppUpdateRepository(private val api: TwoWorkApi) {
    suspend fun latest(): ApiResult<UpdateInfo> = safeApi { api.androidLatest() }
}

/** Skill certificates (freelancer). */
class CertificateRepository(private val api: TwoWorkApi) {
    suspend fun mine(): ApiResult<SkillCertificatesResponse> = safeApi { api.myCertificates() }
}

/** Project categories (public). */
class CategoryRepository(private val api: TwoWorkApi) {
    suspend fun all(): ApiResult<CategoriesResponse> = safeApi { api.categories() }
}

/** Admin extended operations. */
class AdminExtrasRepository(private val api: TwoWorkApi) {
    suspend fun adjustWallet(userId: String, amount: Double, reason: String): ApiResult<AdminWalletAdjustResponse> =
        safeApi { api.adminAdjustWallet(userId, AdminWalletAdjustRequest(amount, reason)) }
    suspend fun createUser(email: String, fullName: String, role: String, password: String): ApiResult<Unit> =
        safeApi { api.adminCreateUser(AdminCreateUserRequest(email, fullName, role, password)); Unit }
    suspend fun resetPassword(userId: String, newPassword: String): ApiResult<Unit> =
        safeApi { api.adminResetPassword(userId, AdminResetPasswordRequest(newPassword)); Unit }
    suspend fun questions(skill: String? = null, level: Int? = null): ApiResult<AdminQuestionsResponse> =
        safeApi { api.adminQuestions(skill, level) }
    suspend fun createQuestion(body: AdminQuestionRequest): ApiResult<Unit> =
        safeApi { api.adminCreateQuestion(body); Unit }
    suspend fun updateQuestion(id: String, body: AdminQuestionRequest): ApiResult<Unit> =
        safeApi { api.adminUpdateQuestion(id, body); Unit }
    suspend fun deleteQuestion(id: String): ApiResult<Unit> =
        safeApi { api.adminDeleteQuestion(id); Unit }
}
