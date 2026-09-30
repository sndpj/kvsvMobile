package com.tekadi.kvvs.league.ui.admin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tekadi.kvvs.league.data.CurrentUser
import com.tekadi.kvvs.league.network.RetrofitClient
import com.tekadi.kvvs.league.network.RoleOptionResponse
import com.tekadi.kvvs.league.network.UpdateUserRoleRequest
import com.tekadi.kvvs.league.network.UserDetailResponse
import com.tekadi.kvvs.league.network.friendlyErrorMessage
import kotlinx.coroutines.launch

/**
 * Feature: Super Admin role assign/revoke — "Show all the user details with roles drop down,
 * from there super admin can update the role, and reset the token for that particular user so
 * he should be login again with new role."
 */
class UserEditViewModel(app: Application) : AndroidViewModel(app) {

    var user by mutableStateOf<UserDetailResponse?>(null)
    var roles by mutableStateOf<List<RoleOptionResponse>>(emptyList())
    var selectedRoleId by mutableStateOf<Int?>(null)

    var loading by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    fun load(userId: Long) {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val userRes = RetrofitClient.api.getUserDetail(userId)
                val rolesRes = RetrofitClient.api.listRoles()

                if (userRes.isSuccessful) {
                    user = userRes.body()
                    selectedRoleId = userRes.body()?.roleId
                } else {
                    error = userRes.friendlyErrorMessage("Couldn't load this user (HTTP ${userRes.code()})")
                }
                if (rolesRes.isSuccessful) {
                    roles = rolesRes.body().orEmpty()
                } else if (error == null) {
                    error = rolesRes.friendlyErrorMessage("Couldn't load roles (HTTP ${rolesRes.code()})")
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    /**
     * "update the role, and reset the token for that particular user so he should be login
     * again with new role" — one backend call does both (see UpdateUserRoleRequest on the
     * backend). The client-side guard here mirrors UserAdminService.updateRole exactly, so a
     * disallowed change never even reaches the network.
     */
    fun saveRole(userId: Long, onSaved: () -> Unit) {
        val current = user
        val blockReason = roleUpdateBlockReason(
            targetUserId = userId,
            actingUserId = CurrentUser.userId,
            selectedRoleId = selectedRoleId,
            currentRoleId = current?.roleId,
        )
        if (blockReason != null) {
            error = blockReason
            return
        }
        val roleId = selectedRoleId ?: return // unreachable — guarded above

        saving = true; error = null
        viewModelScope.launch {
            try {
                val res = RetrofitClient.api.updateUserRole(userId, UpdateUserRoleRequest(roleId))
                if (res.isSuccessful) {
                    val updated = res.body()
                    user = updated
                    selectedRoleId = updated?.roleId
                    info = "Role updated to ${updated?.roleName ?: "the new role"}. " +
                        "${updated?.fullName ?: "This user"} will need to sign in again to pick it up."
                    onSaved()
                } else {
                    error = res.friendlyErrorMessage(
                        when (res.code()) {
                            403 -> "You need Super Admin to change roles"
                            404 -> "This user no longer exists"
                            else -> "Couldn't update the role (HTTP ${res.code()})"
                        },
                    )
                }
            } catch (e: Exception) {
                error = "Couldn't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                saving = false
            }
        }
    }
}
