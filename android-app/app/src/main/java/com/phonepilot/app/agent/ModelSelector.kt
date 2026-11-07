package com.phonepilot.app.agent

import com.phonepilot.app.data.ai.AiProviderRepository
import com.phonepilot.app.data.ai.AiModelSpec
import com.phonepilot.app.data.ai.ModelCapability

/**
 * مSelector النموذج (Phase 7 — Agent Orchestration).
 *
 * يختار النموذج الأنسب لمهمة معينة بناءً على القدرات المثبتة.
 * لا يُرسل صوراً لنموذج لا يدعم VISION.
 */
class ModelSelector(
    private val providerRepository: AiProviderRepository = AiProviderRepository,
) {

    /**
     * يختار النموذج الأنسب للمهمة.
     *
     * يعيد AiModelSpec أو null إذا لم يكن هناك نموذج متاح يناسب المتطلبات.
     */
    fun selectModel(requireVision: Boolean = false, requireReasoning: Boolean = false): AiModelSpec? {
        val provider = providerRepository.selectedProvider() ?: return null
        val models = provider.models

        val filtered = models.filter { model ->
            val hasVision = model.capabilities.any { it.capability == ModelCapability.VISION }
            val hasReasoning = model.capabilities.any { it.capability == ModelCapability.REASONING }

            if (requireVision && !hasVision) return@filter false
            if (requireReasoning && !hasReasoning) return@filter false
            true
        }

        return filtered.firstOrNull()
    }

    /**
     * يتحقق من أن النموذج يدعمvision.
     */
    fun isVisionCapable(model: AiModelSpec): Boolean {
        return model.capabilities.any { it.capability == ModelCapability.VISION }
    }

    /**
     * يتحقق من أن النموذج يدعم الاستدلال.
     */
    fun isReasoningCapable(model: AiModelSpec): Boolean {
        return model.capabilities.any { it.capability == ModelCapability.REASONING }
    }
}
