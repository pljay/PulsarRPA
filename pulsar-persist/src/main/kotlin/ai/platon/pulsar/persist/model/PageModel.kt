package ai.platon.pulsar.persist.model

import ai.platon.pulsar.persist.WebPage.u8
import ai.platon.pulsar.persist.gora.generated.GPageModel

/**
 * Created by vincent on 17-8-3.
 * Copyright @ 2013-2017 Platon AI. All rights reserved
 *
 *
 * The core concept of Document Data Model, DDM
 */
class PageModel(
        val pageModel: GPageModel
) {

    @get:Synchronized
    val numGroups get() = fieldGroups.size

    @get:Synchronized
    val numFields get() = fieldGroups.sumOf { it.fields.size }

    @get:Synchronized
    val numNonNullFields get() = fieldGroups.sumOf { it.fields.count { it.value != null } }

    @get:Synchronized
    val numNonBlankFields get() = fieldGroups.sumOf { it.fields.count { !it.value.isNullOrBlank() } }

    @get:Synchronized
    val isEmpty: Boolean get() = fieldGroups.isEmpty()

    @get:Synchronized
    val isNotEmpty: Boolean get() = !isEmpty

    @get:Synchronized
    val fieldGroups get() = pageModel.fieldGroups

    @get:Synchronized
    val boxedFieldGroups get() = fieldGroups.map { FieldGroup.box(it) }

    fun unbox() = pageModel

    @Synchronized
    fun firstOrNull() = if (isEmpty) null else get(0)

    @Synchronized
    operator fun get(i: Int) = FieldGroup.box(fieldGroups[i])

    @Synchronized
    fun add(fieldGroup: FieldGroup) = fieldGroups.add(fieldGroup.unbox())

    @Synchronized
    fun add(index: Int, fieldGroup: FieldGroup) = fieldGroups.add(index, fieldGroup.unbox())

    @Synchronized
    fun emplace(groupId: Int, fields: Map<String, String?>): FieldGroup {
        return emplace(groupId, 0, "", fields)
    }

    @Synchronized
    fun emplace(groupId: Int, groupName: String, fields: Map<String, String?>): FieldGroup {
        return emplace(groupId, 0, groupName, fields)
    }

    @Synchronized
    fun emplace(groupId: Int, parentId: Int, groupName: String, fields: Map<String, String?>): FieldGroup {
        var fieldGroup = fieldGroups.firstOrNull { it.id == groupId.toLong() }
        if (fieldGroup == null) {
            fieldGroup = FieldGroup.newGFieldGroup(groupId, groupName, parentId)
            fieldGroups.add(fieldGroup)
        }

        // fieldGroup.fields = fields
        fieldGroup.fields.putAll(fields.entries.associate { u8(it.key) to it.value })
//        fieldGroup.setDirty()
//        pageModel.setDirty()
        return FieldGroup.box(fieldGroup)
    }

    @Synchronized
    fun findById(groupId: Int): FieldGroup? {
        val gFieldGroup = fieldGroups.firstOrNull { it.id == groupId.toLong() }
        return if (gFieldGroup == null) null else FieldGroup.box(gFieldGroup)
    }

    @Synchronized
    fun remove(groupId: Int) = fieldGroups.removeIf { it.id == groupId.toLong() }

    @Synchronized
    fun clear() = fieldGroups.clear()

    @Synchronized
    fun deepCopy(): PageModel {
        val other = GPageModel.newBuilder(pageModel).build()
        return PageModel(other)
    }

    companion object {
        @JvmStatic
        fun box(pageModel: GPageModel): PageModel {
            return PageModel(pageModel)
        }
    }
}
