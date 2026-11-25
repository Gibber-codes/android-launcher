package codes.gibber.phone.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName

data class PhoneNumber(val number: String, val label: String)

/** One person from your contacts. */
data class Person(
    val contactId: Long,
    val name: String,
    val photo: String?,
    val starred: Boolean,
    val numbers: List<PhoneNumber>,
)

/** Who a number belongs to, when it's in your contacts. */
data class Match(val contactId: Long, val name: String, val photo: String?)

/** A contact's details for editing. */
data class ContactForm(
    val contactId: Long?,
    val rawId: Long?,
    val nameRowId: Long?,
    val name: String,
    val phones: List<FormRow>,
    val emails: List<FormRow>,
)

/** One phone number or email on the edit screen. rowId is null for a new one. */
data class FormRow(val rowId: Long?, val value: String, val type: Int)

object Contacts {
    // Everyone is read once and kept until the contacts change, so a name for a number is a quick
    // look in memory instead of a question to Android for every row (that took about a minute).
    @Volatile private var everyone: List<Person> = emptyList()
    @Volatile private var byKey: Map<String, Match> = emptyMap()
    @Volatile private var fresh = false
    private val lock = Any()

    /** Called when anything in the contacts changes. */
    fun invalidate() {
        fresh = false
    }

    /** The list from the last read, for showing something right away. */
    fun cached(): List<Person> = everyone

    private fun ensure(context: Context) {
        if (fresh) return
        synchronized(lock) {
            if (!fresh) all(context)
        }
    }

    /** The contact a number belongs to, or null. */
    @Suppress("UNUSED_PARAMETER")
    fun lookup(context: Context, number: String?, version: Int = 0): Match? {
        if (number.isNullOrBlank()) return null
        ensure(context)
        val key = Numbers.key(number)
        if (key.isEmpty()) return null
        return byKey[key]
    }

    /** Everyone in your contacts, A to Z, with their numbers. */
    fun all(context: Context): List<Person> {
        val numbers = HashMap<Long, MutableList<PhoneNumber>>()
        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
                null, null, Phone.IS_SUPER_PRIMARY + " DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(1) ?: continue
                    val list = numbers.getOrPut(c.getLong(0)) { mutableListOf() }
                    if (list.none { Numbers.same(it.number, number) }) {
                        list.add(PhoneNumber(number, phoneLabel(context, c.getInt(2), c.getString(3))))
                    }
                }
            }
        } catch (_: Exception) {
        }
        val people = ArrayList<Person>()
        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.STARRED,
                ),
                null, null, ContactsContract.Contacts.SORT_KEY_PRIMARY + " COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    people.add(Person(id, name, c.getString(2), c.getInt(3) == 1, numbers[id] ?: emptyList()))
                }
            }
        } catch (_: Exception) {
            return everyone
        }
        val index = HashMap<String, Match>()
        for (p in people) for (n in p.numbers) {
            val key = Numbers.key(n.number)
            if (key.isNotEmpty() && key !in index) index[key] = Match(p.contactId, p.name, p.photo)
        }
        everyone = people
        byKey = index
        fresh = true
        return people
    }

    fun person(context: Context, contactId: Long): Person? {
        ensure(context)
        return everyone.firstOrNull { it.contactId == contactId } ?: all(context).firstOrNull { it.contactId == contactId }
    }

    /** Everyone, from memory when nothing has changed. */
    fun everyone(context: Context): List<Person> {
        ensure(context)
        return everyone
    }

    private fun phoneLabel(context: Context, type: Int, label: String?): String =
        Phone.getTypeLabel(context.resources, type, label).toString()

    fun setStarred(context: Context, contactId: Long, starred: Boolean) {
        invalidate()
        val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        context.contentResolver.update(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), values, null, null,
        )
    }

    fun delete(context: Context, contactId: Long) {
        invalidate()
        context.contentResolver.delete(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), null, null,
        )
    }

    /** What the edit screen starts with: the contact's own name, numbers and emails, or blanks. */
    fun form(context: Context, contactId: Long?, number: String? = null): ContactForm {
        if (contactId == null) {
            return ContactForm(null, null, null, "", listOf(FormRow(null, number ?: "", Phone.TYPE_MOBILE)), emptyList())
        }
        var rawId: Long? = null
        var nameRow: Long? = null
        var name = ""
        val phones = ArrayList<FormRow>()
        val emails = ArrayList<FormRow>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
            ),
            ContactsContract.Data.CONTACT_ID + "=?", arrayOf(contactId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val rowId = c.getLong(0)
                val raw = c.getLong(1)
                when (c.getString(2)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (nameRow == null) {
                        nameRow = rowId
                        rawId = raw
                        name = c.getString(3) ?: ""
                    }
                    Phone.CONTENT_ITEM_TYPE -> phones.add(FormRow(rowId, c.getString(3) ?: "", c.getInt(4)))
                    Email.CONTENT_ITEM_TYPE -> emails.add(FormRow(rowId, c.getString(3) ?: "", c.getInt(4)))
                }
                if (rawId == null) rawId = raw
            }
        }
        return ContactForm(contactId, rawId, nameRow, name, phones, emails)
    }

    /** Saves the edit screen. Returns the contact's id, or null when Android refused. */
    fun save(context: Context, before: ContactForm, name: String, phones: List<FormRow>, emails: List<FormRow>): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        val rawRef: Int?
        val rawId = before.rawId
        if (rawId == null) {
            val (type, accountName) = bestAccount(context)
            rawRef = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, type)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                    .build(),
            )
        } else {
            rawRef = null
        }

        fun insertRow(mime: String): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.MIMETYPE, mime)
            return if (rawRef != null) b.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawRef)
            else b.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
        }

        fun rowUri(id: Long) = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id)

        // Name
        val nameRowId = before.nameRowId
        if (nameRowId != null) {
            ops.add(
                ContentProviderOperation.newUpdate(rowUri(nameRowId))
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .withValue(StructuredName.GIVEN_NAME, null)
                    .withValue(StructuredName.FAMILY_NAME, null)
                    .withValue(StructuredName.MIDDLE_NAME, null)
                    .build(),
            )
        } else if (name.isNotBlank()) {
            ops.add(insertRow(StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, name).build())
        }

        fun sync(old: List<FormRow>, new: List<FormRow>, mime: String) {
            val kept = new.mapNotNull { it.rowId }.toSet()
            for (row in old) {
                val id = row.rowId ?: continue
                if (id !in kept) ops.add(ContentProviderOperation.newDelete(rowUri(id)).build())
            }
            for (row in new) {
                val id = row.rowId
                if (row.value.isBlank()) {
                    if (id != null) ops.add(ContentProviderOperation.newDelete(rowUri(id)).build())
                    continue
                }
                if (id != null) {
                    val was = old.firstOrNull { it.rowId == id }
                    if (was != null && was.value == row.value && was.type == row.type) continue
                    ops.add(
                        ContentProviderOperation.newUpdate(rowUri(id))
                            .withValue(ContactsContract.Data.DATA1, row.value)
                            .withValue(ContactsContract.Data.DATA2, row.type)
                            .build(),
                    )
                } else {
                    ops.add(
                        insertRow(mime)
                            .withValue(ContactsContract.Data.DATA1, row.value)
                            .withValue(ContactsContract.Data.DATA2, row.type)
                            .build(),
                    )
                }
            }
        }
        sync(before.phones, phones, Phone.CONTENT_ITEM_TYPE)
        sync(before.emails, emails, Email.CONTENT_ITEM_TYPE)

        invalidate()
        return try {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            before.contactId ?: run {
                val newRaw = results.getOrNull(rawRef ?: 0)?.uri?.let { ContentUris.parseId(it) } ?: return null
                contactIdForRaw(context, newRaw)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun contactIdForRaw(context: Context, rawId: Long): Long? =
        context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId),
            arrayOf(ContactsContract.RawContacts.CONTACT_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    /** New contacts go to the account most of yours are in, Google first; else they stay on the phone. */
    private fun bestAccount(context: Context): Pair<String?, String?> {
        val counts = HashMap<Pair<String?, String?>, Int>()
        try {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
                ContactsContract.RawContacts.DELETED + "=0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0) to c.getString(1)
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        } catch (_: Exception) {
        }
        counts.keys.firstOrNull { it.first == "com.google" }?.let { return it }
        return counts.maxByOrNull { it.value }?.key ?: (null to null)
    }
}
