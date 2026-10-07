package io.github.kylinlee.chatsim.data.model

import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact

data class DialpadSuggestion(val contact: Contact, val phoneNumber: PhoneNumber)
