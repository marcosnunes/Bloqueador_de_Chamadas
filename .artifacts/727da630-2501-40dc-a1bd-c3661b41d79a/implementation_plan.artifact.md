# Implementation Plan - Whitelist Sync & Search Fix

The goal is to fix the synchronization bugs in the whitelist management, ensuring that additions and removals are correctly reflected in both search results and the main list, and that the "Sync" button respects manual deletions.

## User Review Required

> [!IMPORTANT]
> **Sync Button Behavior:** I will change the "Sync" button (two arrows) to a **Refresh** action. It will update the names of existing whitelist items if they changed in your phonebook, but it will **not** automatically re-add contacts you have previously removed. This prevents deleted contacts from "haunting" your whitelist.
>
> **Global Search Upgrade:** I will improve the search logic to properly find contacts by both name and number in the system phonebook, ensuring that "ex-members" of your whitelist appear with a `+` icon for easy re-addition.

## Proposed Changes

### [Component] App Logic

#### [MODIFY] [MainActivity.java](file:///D:/Android/Bloqueador_de_chamadas/app/src/main/java/com/bloqueadordechamadas/MainActivity.java)
- **Fix `saveAndRefresh`:** Ensure the adapter's master list (`fullWhitelist`) is updated on every action, even when a search is active. This fixed the "not re-appearing after clearing search" bug.
- **Refine `performGlobalSearch`:**
    - Use a broader query that searches for both names and phone numbers in the Android Contacts database.
    - Maintain strict consistency between the search result icons and the actual whitelist state.
- **Update `forceSyncContacts`:**
    - Remove the automatic "add if missing" logic.
    - Instead, focus on updating the names of items already in the whitelist to match the phonebook.
- **Improve `onItemActionClicked`:** Ensure the visual state (`isInWhitelist`) is updated correctly across all list instances.

## Verification Plan

### Manual Verification
- **Search & Add:** Search for a contact, click `+`. Verify it turns to a trash icon. Clear search. Verify it appears in the main list.
- **Search & Remove:** Search for a whitelisted contact, click trash icon. Verify it turns to a `+` icon (if in phonebook) or disappears (if manual). Clear search. Verify it's gone from the main list.
- **Sync Test:** Manually remove a contact. Click the Sync button. Verify the contact does **not** come back automatically.
- **Name Sync:** Change a contact's name in the system phonebook. Click Sync in the app. Verify the name updates in the whitelist.
