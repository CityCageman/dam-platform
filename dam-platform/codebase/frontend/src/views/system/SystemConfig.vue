<template>
  <div class="page-container config-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">系统配置</h2>
        <p class="page-desc">统一维护用户、角色、授权、导航菜单与数据字典。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="refreshing" @click="refreshActiveTab">
          <el-icon><Refresh /></el-icon>刷新当前页
        </el-button>
      </div>
    </div>

    <div class="panel config-panel">
      <el-tabs v-model="activeTab" class="config-tabs" @tab-change="handleTabChange">
        <!-- ==================== 用户管理 ==================== -->
        <el-tab-pane label="用户管理" name="users">
          <div class="toolbar">
            <el-input
              v-model="userQuery.keyword"
              placeholder="搜索用户名 / 姓名 / 部门"
              clearable
              style="width: 240px;"
              @keyup.enter="searchUsers"
              @clear="searchUsers"
            />
            <el-select v-model="userQuery.status" placeholder="全部状态" clearable style="width: 130px;" @change="searchUsers">
              <el-option label="启用" value="1" />
              <el-option label="停用" value="0" />
            </el-select>
            <el-button type="primary" @click="searchUsers">查询</el-button>
            <el-button @click="resetUserQuery">重置</el-button>
            <span class="toolbar-total">共 {{ userTotal }} 条</span>
            <el-button type="primary" @click="openUserForm(null)">
              <el-icon><Plus /></el-icon>新增用户
            </el-button>
          </div>

          <el-alert v-if="userError" class="data-error" type="warning" :closable="false" show-icon :title="userError" />

          <el-table v-if="userLoading || users.length" v-loading="userLoading" :data="users" stripe>
            <el-table-column prop="username" label="用户名" min-width="130" show-overflow-tooltip />
            <el-table-column label="姓名" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.realName || '-' }}</template>
            </el-table-column>
            <el-table-column label="部门" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.department || '-' }}</template>
            </el-table-column>
            <el-table-column label="联系方式" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.phone || row.email || '-' }}</span>
                <span v-if="row.phone && row.email" class="muted"> / {{ row.email }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusEnabled(row.status) ? 'success' : 'info'">
                  {{ statusEnabled(row.status) ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近登录" width="170">
              <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button text size="small" class="table-op" @click="openUserForm(row)">编辑</el-button>
                <el-button text size="small" class="table-op" @click="openResetPassword(row)">重置密码</el-button>
                <el-button text size="small" type="danger" class="table-op" @click="removeUser(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无用户数据" :image-size="60" />

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="userQuery.page"
              v-model:page-size="userQuery.pageSize"
              :total="userTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              small
              background
              @change="loadUsers"
            />
          </div>
        </el-tab-pane>

        <!-- ==================== 角色管理 ==================== -->
        <el-tab-pane label="角色管理" name="roles">
          <div class="toolbar">
            <el-input
              v-model="roleQuery.keyword"
              placeholder="搜索角色编码 / 名称"
              clearable
              style="width: 240px;"
              @keyup.enter="searchRoles"
              @clear="searchRoles"
            />
            <el-select v-model="roleQuery.status" placeholder="全部状态" clearable style="width: 130px;" @change="searchRoles">
              <el-option label="启用" value="1" />
              <el-option label="停用" value="0" />
            </el-select>
            <el-button type="primary" @click="searchRoles">查询</el-button>
            <el-button @click="resetRoleQuery">重置</el-button>
            <span class="toolbar-total">共 {{ roleTotal }} 条</span>
            <el-button type="primary" @click="openRoleForm(null)">
              <el-icon><Plus /></el-icon>新增角色
            </el-button>
          </div>

          <el-alert v-if="roleError" class="data-error" type="warning" :closable="false" show-icon :title="roleError" />

          <el-table v-if="roleLoading || roles.length" v-loading="roleLoading" :data="roles" stripe>
            <el-table-column label="角色编码" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.roleCode || '-' }}</template>
            </el-table-column>
            <el-table-column label="角色名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.roleName || '-' }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusEnabled(row.status) ? 'success' : 'info'">
                  {{ statusEnabled(row.status) ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button text size="small" class="table-op" @click="openRoleForm(row)">编辑</el-button>
                <el-button text size="small" type="danger" class="table-op" @click="removeRole(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无角色数据" :image-size="60" />

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="roleQuery.page"
              v-model:page-size="roleQuery.pageSize"
              :total="roleTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              small
              background
              @change="loadRoles"
            />
          </div>
        </el-tab-pane>

        <!-- ==================== 授权管理 ==================== -->
        <el-tab-pane label="授权管理" name="authorization">
          <el-alert v-if="authOptionsError" class="data-error" type="warning" :closable="false" show-icon :title="authOptionsError" />

          <el-tabs v-model="authMode" class="auth-tabs">
            <el-tab-pane label="用户授权" name="user">
              <div class="auth-grid" v-loading="authOptionsLoading">
                <div class="auth-col">
                  <h4 class="panel-title">选择用户</h4>
                  <el-select
                    v-model="selectedAuthUserId"
                    filterable
                    placeholder="请选择用户"
                    style="width: 100%;"
                    @change="loadUserRoleAssignment"
                  >
                    <el-option v-for="u in authUsers" :key="u.id" :label="userLabel(u)" :value="u.id" />
                  </el-select>
                  <el-empty v-if="!authOptionsLoading && !authUsers.length" description="暂无用户数据" :image-size="48" />
                  <p class="auth-hint">为用户分配角色，保存后立即生效。</p>
                </div>

                <div class="auth-col">
                  <h4 class="panel-title">分配角色</h4>
                  <el-alert v-if="userRoleError" class="data-error" type="warning" :closable="false" show-icon :title="userRoleError" />
                  <div class="auth-check-wrap" v-loading="userRoleLoading">
                    <el-checkbox-group v-model="selectedUserRoleIds" class="auth-check-list">
                      <el-checkbox v-for="role in authRoles" :key="role.id" :label="role.id">
                        <span>{{ role.roleName || role.roleCode || ('角色 ' + role.id) }}</span>
                        <span v-if="role.roleCode" class="auth-role-code">{{ role.roleCode }}</span>
                      </el-checkbox>
                    </el-checkbox-group>
                    <el-empty v-if="!userRoleLoading && !authRoles.length" description="暂无可分配角色" :image-size="48" />
                  </div>
                  <div class="auth-actions">
                    <el-button type="primary" :loading="userRoleSaving" :disabled="!selectedAuthUserId" @click="saveUserRoleAssignment">
                      保存用户角色
                    </el-button>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="角色授权" name="role">
              <div class="auth-grid" v-loading="authOptionsLoading">
                <div class="auth-col">
                  <h4 class="panel-title">选择角色</h4>
                  <el-select
                    v-model="selectedAuthRoleId"
                    filterable
                    placeholder="请选择角色"
                    style="width: 100%;"
                    @change="loadRoleMenuAssignment"
                  >
                    <el-option v-for="r in authRoles" :key="r.id" :label="roleLabel(r)" :value="r.id" />
                  </el-select>
                  <el-empty v-if="!authOptionsLoading && !authRoles.length" description="暂无角色数据" :image-size="48" />
                  <p class="auth-hint">为角色勾选可见菜单，保存后按角色生效。</p>
                </div>

                <div class="auth-col">
                  <h4 class="panel-title">分配菜单</h4>
                  <el-alert v-if="roleMenuError" class="data-error" type="warning" :closable="false" show-icon :title="roleMenuError" />
                  <el-alert v-if="authMenuError" class="data-error" type="warning" :closable="false" show-icon :title="authMenuError" />
                  <div class="auth-tree-wrap" v-loading="roleMenuLoading">
                    <el-tree
                      v-if="authMenuTree.length"
                      ref="authMenuTreeRef"
                      :data="authMenuTree"
                      show-checkbox
                      node-key="id"
                      default-expand-all
                      :props="{ label: 'label', children: 'children' }"
                    />
                    <el-empty v-else description="暂无可分配菜单" :image-size="48" />
                  </div>
                  <div class="auth-actions">
                    <el-button type="primary" :loading="roleMenuSaving" :disabled="!selectedAuthRoleId" @click="saveRoleMenuAssignment">
                      保存角色菜单
                    </el-button>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <!-- ==================== 菜单维护（dam_config navigation） ==================== -->
        <el-tab-pane label="菜单维护" name="menus">
          <div class="toolbar">
            <span class="toolbar-title">{{ menuTypeName }}</span>
            <span class="toolbar-sub">维护 dam_config 导航树，用于门户与模块菜单展示</span>
            <span class="toolbar-total">共 {{ menuNodeCount }} 个节点</span>
            <el-button type="primary" @click="openAddDialog(null)">
              <el-icon><Plus /></el-icon>新增菜单
            </el-button>
          </div>

          <el-alert v-if="menuError" class="data-error" type="warning" :closable="false" show-icon :title="menuError" />

          <div class="menu-tree-wrap" v-loading="loading">
            <el-tree
              v-if="treeData.length"
              ref="treeRef"
              :data="treeData"
              node-key="id"
              :props="{ label: 'configName', children: 'children' }"
              highlight-current
              default-expand-all
            >
              <template #default="{ data }">
                <div class="tree-node">
                  <img v-if="isImageIcon(data.icon)" :src="iconSrc(data.icon)" class="tree-icon-img" />
                  <el-icon v-else><component :is="data.icon || 'Folder'" /></el-icon>
                  <span>{{ data.configName }}</span>
                  <span v-if="data.configCode" class="node-code">({{ data.configCode }})</span>
                  <el-tag v-if="data.status === 0" size="small" type="info">停用</el-tag>
                  <div class="node-actions">
                    <el-button size="small" text @click.stop="openAddDialog(data)"><el-icon><Plus /></el-icon></el-button>
                    <el-button size="small" text @click.stop="openEditDialog(data)"><el-icon><Edit /></el-icon></el-button>
                    <el-button size="small" text type="danger" @click.stop="handleDelete(data)"><el-icon><Delete /></el-icon></el-button>
                  </div>
                </div>
              </template>
            </el-tree>
            <el-empty v-else description="暂无菜单配置" :image-size="60" />
          </div>
        </el-tab-pane>

        <!-- ==================== 字典维护（sys_dict_type / sys_dict_data） ==================== -->
        <el-tab-pane label="字典维护" name="dict">
          <div class="dict-split">
            <div class="dict-left">
              <div class="dict-toolbar">
                <b>字典类型</b>
                <span>共 {{ dictTypeTotal }} 个</span>
                <div style="flex:1" />
                <el-input
                  v-model="dictTypeKeyword"
                  placeholder="搜索编码/名称"
                  clearable
                  size="small"
                  style="width: 150px"
                  @keyup.enter="searchDictTypes"
                  @clear="searchDictTypes"
                />
                <el-button size="small" type="primary" @click="openDictTypeForm(null)"><el-icon><Plus /></el-icon>新增</el-button>
              </div>
              <el-alert v-if="dictTypeError" class="data-error" type="warning" :closable="false" show-icon :title="dictTypeError" />
              <div class="dict-type-list">
                <div
                  v-for="row in dictTypes"
                  :key="row.id"
                  class="dict-type-item"
                  :class="{ active: currentDictType?.id === row.id }"
                  @click="selectDictType(row)"
                >
                  <div class="dict-type-main">
                    <b>{{ row.dictTypeName }}</b>
                    <span>{{ row.dictTypeCode }}</span>
                  </div>
                  <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
                  <div class="dict-type-ops" @click.stop>
                    <el-button text size="small" @click="openDictTypeForm(row)">编辑</el-button>
                    <el-button text size="small" type="danger" @click="removeDictType(row)">删除</el-button>
                  </div>
                </div>
                <el-empty v-if="!dictTypes.length" description="暂无字典类型" :image-size="56" />
              </div>
              <div class="dict-pager">
                <el-pagination
                  v-model:current-page="dictTypePage"
                  v-model:page-size="dictTypePageSize"
                  :total="dictTypeTotal"
                  layout="total, prev, pager, next"
                  :page-sizes="[10, 20, 50]"
                  small
                  background
                  @change="loadDictTypesPage"
                />
              </div>
            </div>

            <div class="dict-right">
              <div class="dict-toolbar">
                <b>{{ currentDictType?.dictTypeName || '字典数据' }}</b>
                <span v-if="currentDictType">
                  {{ currentDictType.dictTypeCode }}<template v-if="currentDictType.remark"> · {{ currentDictType.remark }}</template>
                </span>
                <div style="flex:1" />
                <el-button size="small" type="primary" :disabled="!currentDictType" @click="openDictDataForm(null)">
                  <el-icon><Plus /></el-icon>新增数据
                </el-button>
              </div>
              <el-alert v-if="dictDataError" class="data-error" type="warning" :closable="false" show-icon :title="dictDataError" />
              <div class="dict-data-table">
                <el-table :data="dictDataList" size="small" stripe height="100%" v-loading="dictDataLoading">
                  <el-table-column prop="dictLabel" label="标签" :width="dictLabelWidth" min-width="110" show-overflow-tooltip />
                  <el-table-column prop="dictValue" label="值" :width="dictValueWidth" min-width="90" show-overflow-tooltip />
                  <el-table-column prop="dictExtra" label="扩展值" :width="dictExtraWidth" min-width="80" show-overflow-tooltip />
                  <el-table-column prop="sortOrder" label="排序" width="60" />
                  <el-table-column label="默认" width="60">
                    <template #default="{ row }">{{ row.isDefault ? '是' : '' }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="70">
                    <template #default="{ row }">
                      <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="130">
                    <template #default="{ row }">
                      <el-button size="small" text class="dict-op-btn" @click.stop="openDictDataForm(row)">编辑</el-button>
                      <el-button size="small" text type="danger" class="dict-op-btn" @click.stop="removeDictData(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-if="!currentDictType || (dictDataError === '' && !dictDataLoading && !dictDataList.length)" description="该类型下暂无字典数据" :image-size="56" />
              </div>
              <div class="dict-pager">
                <el-pagination
                  v-model:current-page="dictDataPage"
                  v-model:page-size="dictDataPageSize"
                  :total="dictDataTotal"
                  layout="total, prev, pager, next"
                  :page-sizes="[10, 20, 50]"
                  small
                  background
                  @change="loadDictDataPage"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 用户新增/编辑 -->
    <el-dialog v-model="showUserForm" :title="isEditUser ? '编辑用户' : '新增用户'" width="520px" top="10vh">
      <el-form :model="userForm" label-width="90px" size="small">
        <el-form-item label="用户名" required>
          <el-input v-model="userForm.username" placeholder="登录账号" :disabled="isEditUser" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="userForm.department" placeholder="所属科室/部门" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item v-if="!isEditUser" label="初始密码" required>
          <el-input v-model="userForm.password" type="password" show-password placeholder="新增用户时设置初始密码" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="userForm._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserForm = false">取消</el-button>
        <el-button :loading="userSaving" type="primary" @click="submitUserForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="showResetPassword" title="重置密码" width="440px" top="15vh">
      <el-form :model="{ password: resetPasswordValue }" label-width="90px" size="small">
        <el-form-item label="用户">
          <el-input :model-value="resetTarget ? userLabel(resetTarget) : '-'" disabled />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="resetPasswordValue" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showResetPassword = false">取消</el-button>
        <el-button :loading="resetPasswordSaving" type="primary" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 角色新增/编辑 -->
    <el-dialog v-model="showRoleForm" :title="isEditRole ? '编辑角色' : '新增角色'" width="500px" top="12vh">
      <el-form :model="roleForm" label-width="90px" size="small">
        <el-form-item label="角色编码" required>
          <el-input v-model="roleForm.roleCode" placeholder="如 DATA_STEWARD" :disabled="isEditRole" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="如 数据管家" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" placeholder="角色职责说明（可选）" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="roleForm._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleForm = false">取消</el-button>
        <el-button :loading="roleSaving" type="primary" @click="submitRoleForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 菜单节点新增/编辑 -->
    <el-dialog v-model="showMenuForm" :title="isEditMenu ? '编辑菜单' : '新增菜单'" width="500px" top="10vh">
      <el-form :model="menuForm" label-width="100px" size="small">
        <el-form-item label="配置类型"><el-input :model-value="menuTypeName" disabled /></el-form-item>
        <el-form-item label="父节点"><el-input :model-value="menuForm._parentName || '（顶级）'" disabled /></el-form-item>
        <el-form-item label="节点编码" required>
          <el-input v-model="menuForm.configCode" placeholder="如 database_table" :disabled="isEditMenu" />
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="menuForm.configName" placeholder="如 数据库表" />
        </el-form-item>
        <el-form-item label="图标">
          <div class="icon-field">
            <el-input v-model="menuForm.icon" placeholder="如 Folder, Table, Cpu" style="flex:1;" />
            <el-button size="small" @click="showIconPicker = true">图标库</el-button>
          </div>
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="menuForm.configValue" type="textarea" :rows="4" placeholder="配置值（可选）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="menuForm.description" placeholder="用途说明（可选）" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="menuForm.sortOrder" :min="0" :max="9999" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="menuForm._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMenuForm = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submitMenuForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典类型维护 -->
    <el-dialog v-model="showDictTypeForm" :title="isEditDictType ? '编辑字典类型' : '新增字典类型'" width="460px" top="15vh">
      <el-form :model="dictTypeForm" label-width="90px" size="small">
        <el-form-item label="类型编码" required>
          <el-input v-model="dictTypeForm.dictTypeCode" placeholder="如 asset_type、ticket_status" :disabled="isEditDictType" />
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="dictTypeForm.dictTypeName" placeholder="如 资产类型、工单状态" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dictTypeForm.remark" type="textarea" :rows="2" placeholder="说明该字典被哪些字段引用" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dictTypeForm._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDictTypeForm = false">取消</el-button>
        <el-button type="primary" @click="submitDictTypeForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据维护 -->
    <el-dialog v-model="showDictDataForm" :title="isEditDictData ? '编辑字典数据' : '新增字典数据'" width="460px" top="15vh">
      <el-form :model="dictDataForm" label-width="90px" size="small">
        <el-form-item label="所属类型"><el-input :model-value="currentDictType?.dictTypeName || '-'" disabled /></el-form-item>
        <el-form-item label="字典标签" required>
          <el-input v-model="dictDataForm.dictLabel" placeholder="界面展示名称，如 待处理" />
        </el-form-item>
        <el-form-item label="字典值" required>
          <el-input v-model="dictDataForm.dictValue" placeholder="代码存储的枚举值，如 0" />
        </el-form-item>
        <el-form-item label="扩展值">
          <el-input v-model="dictDataForm.dictExtra" placeholder="样式类名/颜色等 UI 辅助信息（可选）" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="dictDataForm.sortOrder" :min="0" :max="9999" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="默认值">
          <el-switch v-model="dictDataForm.isDefault" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dictDataForm._enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dictDataForm.remarks" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDictDataForm = false">取消</el-button>
        <el-button type="primary" @click="submitDictDataForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 图标选择器 -->
    <el-dialog v-model="showIconPicker" title="选择图标" width="640px" top="5vh">
      <el-tabs v-model="iconTab">
        <el-tab-pane label="图片图标" name="image">
          <el-input v-model="iconKeyword" placeholder="搜索图标名称（支持中文/英文/文件名）" clearable size="small" style="margin-bottom: 8px;" />
          <div v-for="g in imageIconGroups" :key="g.group" class="icon-group">
            <div class="icon-group-title">{{ g.group }}（{{ g.items.length }}）</div>
            <div class="icon-grid">
              <div
                v-for="it in g.items"
                :key="it.path"
                class="icon-item"
                :class="{ active: menuForm.icon === it.path }"
                :title="it.path"
                @click="selectImageIcon(it.path)"
              >
                <img :src="iconSrc(it.path)" class="icon-img" />
                <span class="icon-name">{{ it.name }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!imageIcons.length" description="未找到图标图片，请确认 public/image 下已生成 index.json" :image-size="60" />
        </el-tab-pane>
        <el-tab-pane label="组件图标" name="component">
          <div class="icon-grid">
            <div v-for="icon in iconList" :key="icon" class="icon-item" :class="{ active: menuForm.icon === icon }" @click="selectIcon(icon)">
              <el-icon :size="20"><component :is="icon" /></el-icon>
              <span class="icon-name">{{ icon }}</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { catalogApi, configApi, systemApi } from '@/api'
import type {
  PageQuery,
  SystemRole,
  SystemRolePayload,
  SystemUser,
  SystemUserPayload
} from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { encryptPassword } from '@/utils/crypto'

/** dam_config 中导航菜单的类型编码：菜单维护页只操作该类型 */
const MENU_CONFIG_TYPE = 'navigation'
const menuTypeName = computed(() => configApi.getTypes().find(t => t.type === MENU_CONFIG_TYPE)?.name || '菜单维护')

const activeTab = ref('users')
const refreshing = ref(false)

// ==================== 通用工具 ====================

/** 兼容后端分页对象 / 纯数组两种返回结构 */
function normalizePage<T>(payload: any): { records: T[]; total: number } {
  if (Array.isArray(payload)) return { records: payload as T[], total: payload.length }
  const records = Array.isArray(payload?.records)
    ? payload.records
    : Array.isArray(payload?.rows)
      ? payload.rows
      : Array.isArray(payload?.list)
        ? payload.list
        : Array.isArray(payload?.data)
          ? payload.data
          : []
  return { records: records as T[], total: Number(payload?.total ?? payload?.totalCount ?? records.length ?? 0) }
}

/** 状态兼容：数字 0/1 与 ENABLED/ACTIVE 等字符串；缺省视为启用 */
function statusEnabled(status: any): boolean {
  if (status === undefined || status === null || status === '') return true
  if (typeof status === 'number') return status === 1
  if (typeof status === 'boolean') return status
  const value = String(status).toUpperCase()
  return value === '1' || value === 'ENABLED' || value === 'ACTIVE' || value === 'NORMAL'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function userLabel(user: SystemUser) {
  const name = user.realName || user.username || `用户 ${user.id}`
  return user.realName && user.username ? `${name}（${user.username}）` : name
}

function roleLabel(role: SystemRole) {
  const name = role.roleName || role.roleCode || `角色 ${role.id}`
  return role.roleCode && role.roleName ? `${name}（${role.roleCode}）` : name
}

/** 从授权接口返回值中提取角色/菜单 ID 列表（兼容 ID 数组、对象数组、包装对象） */
function extractIds(payload: any, key: 'role' | 'menu'): number[] {
  if (!payload) return []
  const idKey = key === 'role' ? 'roleIds' : 'menuIds'
  const pluralKey = key === 'role' ? 'roles' : 'menus'
  const list = Array.isArray(payload)
    ? payload
    : Array.isArray(payload[idKey])
      ? payload[idKey]
      : Array.isArray(payload[pluralKey])
        ? payload[pluralKey]
        : Array.isArray(payload.records)
          ? payload.records
          : []
  return list
    .map((item: any) => {
      if (item === null || item === undefined) return Number.NaN
      if (typeof item === 'object') return Number(item.id ?? item[`${key}Id`])
      return Number(item)
    })
    .filter((id: number) => Number.isFinite(id))
}

// ==================== 用户管理 ====================

const userQuery = ref<PageQuery>({ keyword: '', status: '', page: 1, pageSize: 10 })
const users = ref<SystemUser[]>([])
const userTotal = ref(0)
const userLoading = ref(false)
const userLoaded = ref(false)
const userError = ref('')

const showUserForm = ref(false)
const isEditUser = ref(false)
const userSaving = ref(false)
const userForm = ref<any>({})

const showResetPassword = ref(false)
const resetTarget = ref<SystemUser | null>(null)
const resetPasswordValue = ref('')
const resetPasswordSaving = ref(false)

async function loadUsers() {
  userLoading.value = true
  userError.value = ''
  try {
    const res = await systemApi.users.list({
      ...userQuery.value,
      keyword: userQuery.value.keyword?.trim() || undefined,
      status: userQuery.value.status || undefined
    })
    const page = normalizePage<SystemUser>(res.data)
    users.value = page.records
    userTotal.value = page.total
  } catch (e: any) {
    users.value = []
    userTotal.value = 0
    userError.value = `用户数据加载失败：${e?.message || '请稍后重试'}`
  } finally {
    userLoading.value = false
    userLoaded.value = true
  }
}

function searchUsers() {
  userQuery.value.page = 1
  loadUsers()
}

function resetUserQuery() {
  userQuery.value = { keyword: '', status: '', page: 1, pageSize: userQuery.value.pageSize }
  loadUsers()
}

function openUserForm(row: any = null) {
  isEditUser.value = !!row
  userForm.value = row
    ? { ...row, _enabled: statusEnabled(row.status) }
    : { username: '', realName: '', department: '', email: '', phone: '', password: '', status: 1, _enabled: true }
  showUserForm.value = true
}

async function submitUserForm() {
  const form = userForm.value
  if (!String(form.username || '').trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  if (!isEditUser.value && !form.password) {
    ElMessage.warning('请设置初始密码')
    return
  }
  userSaving.value = true
  try {
    const payload: SystemUserPayload = {
      username: String(form.username).trim(),
      realName: form.realName || '',
      department: form.department || '',
      email: form.email || '',
      phone: form.phone || '',
      status: form._enabled ? 1 : 0
    }
    if (!isEditUser.value) {
      // 与登录一致，使用 auth 服务 RSA 公钥加密初始密码
      payload.password = await encryptPassword(String(form.password), 'auth')
      await systemApi.users.create(payload)
    } else {
      await systemApi.users.update(form.id, payload)
    }
    ElMessage.success(isEditUser.value ? '用户已更新' : '用户已创建')
    showUserForm.value = false
    await loadUsers()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    userSaving.value = false
  }
}

function openResetPassword(row: any) {
  resetTarget.value = row
  resetPasswordValue.value = ''
  showResetPassword.value = true
}

async function submitResetPassword() {
  if (!resetTarget.value) return
  if (!resetPasswordValue.value || resetPasswordValue.value.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  resetPasswordSaving.value = true
  try {
    const password = await encryptPassword(resetPasswordValue.value, 'auth')
    await systemApi.users.resetPassword(resetTarget.value.id, { password })
    ElMessage.success('密码已重置')
    showResetPassword.value = false
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    resetPasswordSaving.value = false
  }
}

async function removeUser(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.realName || row.username}」？`, '删除确认', { type: 'warning' })
  } catch (_e) {
    return
  }
  try {
    await systemApi.users.remove(row.id)
    ElMessage.success('用户已删除')
    if (users.value.length === 1 && Number(userQuery.value.page || 1) > 1) {
      userQuery.value.page = Number(userQuery.value.page) - 1
    }
    await loadUsers()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  }
}

// ==================== 角色管理 ====================

const roleQuery = ref<PageQuery>({ keyword: '', status: '', page: 1, pageSize: 10 })
const roles = ref<SystemRole[]>([])
const roleTotal = ref(0)
const roleLoading = ref(false)
const roleLoaded = ref(false)
const roleError = ref('')

const showRoleForm = ref(false)
const isEditRole = ref(false)
const roleSaving = ref(false)
const roleForm = ref<any>({})

async function loadRoles() {
  roleLoading.value = true
  roleError.value = ''
  try {
    const res = await systemApi.roles.list({
      ...roleQuery.value,
      keyword: roleQuery.value.keyword?.trim() || undefined,
      status: roleQuery.value.status || undefined
    })
    const page = normalizePage<SystemRole>(res.data)
    roles.value = page.records
    roleTotal.value = page.total
  } catch (e: any) {
    roles.value = []
    roleTotal.value = 0
    roleError.value = `角色数据加载失败：${e?.message || '请稍后重试'}`
  } finally {
    roleLoading.value = false
    roleLoaded.value = true
  }
}

function searchRoles() {
  roleQuery.value.page = 1
  loadRoles()
}

function resetRoleQuery() {
  roleQuery.value = { keyword: '', status: '', page: 1, pageSize: roleQuery.value.pageSize }
  loadRoles()
}

function openRoleForm(row: any = null) {
  isEditRole.value = !!row
  roleForm.value = row
    ? { ...row, _enabled: statusEnabled(row.status) }
    : { roleCode: '', roleName: '', description: '', status: 1, _enabled: true }
  showRoleForm.value = true
}

async function submitRoleForm() {
  const form = roleForm.value
  if (!String(form.roleCode || '').trim() || !String(form.roleName || '').trim()) {
    ElMessage.warning('请填写角色编码和名称')
    return
  }
  roleSaving.value = true
  try {
    const payload: SystemRolePayload = {
      roleCode: String(form.roleCode).trim(),
      roleName: String(form.roleName).trim(),
      description: form.description || '',
      status: form._enabled ? 1 : 0
    }
    if (isEditRole.value) await systemApi.roles.update(form.id, payload)
    else await systemApi.roles.create(payload)
    ElMessage.success(isEditRole.value ? '角色已更新' : '角色已创建')
    showRoleForm.value = false
    roleLoaded.value = false
    await loadRoles()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    roleSaving.value = false
  }
}

async function removeRole(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.roleName || row.roleCode}」？`, '删除确认', { type: 'warning' })
  } catch (_e) {
    return
  }
  try {
    await systemApi.roles.remove(row.id)
    ElMessage.success('角色已删除')
    if (roles.value.length === 1 && Number(roleQuery.value.page || 1) > 1) {
      roleQuery.value.page = Number(roleQuery.value.page) - 1
    }
    roleLoaded.value = false
    await loadRoles()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  }
}

// ==================== 授权管理 ====================

interface AuthMenuNode {
  id: number
  label: string
  children: AuthMenuNode[]
}

const authMode = ref('user')
const authUsers = ref<SystemUser[]>([])
const authRoles = ref<SystemRole[]>([])
const authOptionsLoading = ref(false)
const authOptionsError = ref('')

const selectedAuthUserId = ref<number | null>(null)
const selectedUserRoleIds = ref<number[]>([])
const userRoleLoading = ref(false)
const userRoleSaving = ref(false)
const userRoleError = ref('')

const selectedAuthRoleId = ref<number | null>(null)
const authMenuTree = ref<AuthMenuNode[]>([])
const authMenuTreeRef = ref<any>()
const authMenuError = ref('')
const roleMenuLoading = ref(false)
const roleMenuSaving = ref(false)
const roleMenuError = ref('')

/** 把菜单平铺列表（或树）转换为统一的菜单树 */
function buildMenuTree(list: any[]): AuthMenuNode[] {
  const flat: any[] = []
  const walk = (items: any[]) => {
    for (const item of items) {
      if (!item || typeof item !== 'object') continue
      flat.push(item)
      if (Array.isArray(item.children)) walk(item.children)
    }
  }
  walk(list)

  const map = new Map<number, AuthMenuNode>()
  for (const item of flat) {
    const id = Number(item.id ?? item.menuId)
    if (!Number.isFinite(id) || map.has(id)) continue
    map.set(id, {
      id,
      label: String(item.menuName || item.configName || item.name || item.label || item.menuCode || `菜单 ${id}`),
      children: []
    })
  }

  const roots: AuthMenuNode[] = []
  for (const item of flat) {
    const id = Number(item.id ?? item.menuId)
    const node = map.get(id)
    if (!node) continue
    const parentId = item.parentId ?? item.parent_id
    const parent = parentId === null || parentId === undefined ? undefined : map.get(Number(parentId))
    if (parent && parent.id !== node.id) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

async function ensureAuthMenus(force = false) {
  if (!force && authMenuTree.value.length) return
  authMenuError.value = ''
  try {
    const res = await systemApi.menus.list()
    const payload: any = res.data
    const list = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.records)
        ? payload.records
        : Array.isArray(payload?.menus)
          ? payload.menus
          : []
    authMenuTree.value = buildMenuTree(list)
  } catch (e: any) {
    authMenuTree.value = []
    authMenuError.value = `菜单数据加载失败：${e?.message || '请稍后重试'}`
  }
}

async function ensureAuthOptions(force = false) {
  if (authOptionsLoading.value) return
  if (!force && (authUsers.value.length || authRoles.value.length)) {
    if (!selectedAuthUserId.value && authUsers.value.length) {
      selectedAuthUserId.value = authUsers.value[0].id
      await loadUserRoleAssignment()
    }
    if (!selectedAuthRoleId.value && authRoles.value.length) {
      selectedAuthRoleId.value = authRoles.value[0].id
      await loadRoleMenuAssignment()
    }
    return
  }

  authOptionsLoading.value = true
  authOptionsError.value = ''
  try {
    // 用户、角色独立加载，任一接口未就绪时另一侧仍可展示
    const [userResult, roleResult] = await Promise.allSettled([
      systemApi.users.list({ page: 1, pageSize: 200 }),
      systemApi.roles.list({ page: 1, pageSize: 200 })
    ])
    const errors: string[] = []
    if (userResult.status === 'fulfilled') {
      authUsers.value = normalizePage<SystemUser>(userResult.value.data).records
    } else {
      authUsers.value = []
      errors.push(`用户：${userResult.reason?.message || '加载失败'}`)
    }
    if (roleResult.status === 'fulfilled') {
      authRoles.value = normalizePage<SystemRole>(roleResult.value.data).records
    } else {
      authRoles.value = []
      errors.push(`角色：${roleResult.reason?.message || '加载失败'}`)
    }
    authOptionsError.value = errors.length ? `授权选项加载失败（${errors.join('；')}）` : ''

    if (!selectedAuthUserId.value && authUsers.value.length) selectedAuthUserId.value = authUsers.value[0].id
    if (!selectedAuthRoleId.value && authRoles.value.length) selectedAuthRoleId.value = authRoles.value[0].id

    await ensureAuthMenus()
    if (selectedAuthUserId.value) await loadUserRoleAssignment()
    if (selectedAuthRoleId.value) await loadRoleMenuAssignment()
  } catch (e: any) {
    authOptionsError.value = `授权选项加载失败：${e?.message || '请稍后重试'}`
  } finally {
    authOptionsLoading.value = false
  }
}

async function loadUserRoleAssignment() {
  if (!selectedAuthUserId.value) {
    selectedUserRoleIds.value = []
    return
  }
  userRoleLoading.value = true
  userRoleError.value = ''
  try {
    const res = await systemApi.authorizations.getUserRoles(selectedAuthUserId.value)
    selectedUserRoleIds.value = extractIds(res.data, 'role')
  } catch (e: any) {
    selectedUserRoleIds.value = []
    userRoleError.value = `用户角色加载失败：${e?.message || '请稍后重试'}`
  } finally {
    userRoleLoading.value = false
  }
}

async function saveUserRoleAssignment() {
  if (!selectedAuthUserId.value) {
    ElMessage.warning('请先选择用户')
    return
  }
  userRoleSaving.value = true
  try {
    await systemApi.authorizations.saveUserRoles(selectedAuthUserId.value, selectedUserRoleIds.value)
    ElMessage.success('用户角色已保存')
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    userRoleSaving.value = false
  }
}

async function loadRoleMenuAssignment() {
  if (!selectedAuthRoleId.value) {
    roleMenuError.value = ''
    return
  }
  roleMenuLoading.value = true
  roleMenuError.value = ''
  try {
    await ensureAuthMenus()
    const res = await systemApi.authorizations.getRoleMenus(selectedAuthRoleId.value)
    const ids = extractIds(res.data, 'menu')
    await nextTick()
    authMenuTreeRef.value?.setCheckedKeys(ids, false)
  } catch (e: any) {
    roleMenuError.value = `角色菜单加载失败：${e?.message || '请稍后重试'}`
    await nextTick()
    authMenuTreeRef.value?.setCheckedKeys([], false)
  } finally {
    roleMenuLoading.value = false
  }
}

async function saveRoleMenuAssignment() {
  const roleId = selectedAuthRoleId.value
  if (!roleId) {
    ElMessage.warning('请先选择角色')
    return
  }
  const tree = authMenuTreeRef.value
  if (!tree) {
    ElMessage.warning('菜单尚未加载')
    return
  }
  const checkedKeys = [
    ...(tree.getCheckedKeys(false) || []),
    ...(tree.getHalfCheckedKeys() || [])
  ].map((id: any) => Number(id)).filter((id: number) => Number.isFinite(id))

  roleMenuSaving.value = true
  try {
    await systemApi.authorizations.saveRoleMenus(roleId, checkedKeys)
    ElMessage.success('角色菜单已保存')
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    roleMenuSaving.value = false
  }
}

// ==================== 菜单维护（dam_config navigation） ====================

const treeData = ref<any[]>([])
const loading = ref(false)
const menuError = ref('')
const flatMap = new Map<number, any>()

const showMenuForm = ref(false)
const isEditMenu = ref(false)
const saving = ref(false)
const menuForm = ref<any>({})

const showIconPicker = ref(false)
const iconTab = ref('image')
const iconKeyword = ref('')
const imageIcons = ref<{ group: string; name: string; path: string }[]>([])

const iconList = [
  'FolderOpened', 'Folder', 'Document', 'DataBoard', 'Connection', 'Cpu', 'Table', 'File', 'Setting',
  'Search', 'Edit', 'Delete', 'Plus', 'Refresh', 'Download', 'Upload', 'View', 'CopyDocument',
  'QuestionFilled', 'DataAnalysis', 'List', 'Menu', 'HomeFilled', 'Bell', 'User', 'Link',
  'ChatDotSquare', 'Timer', 'Collection', 'Tickets', 'InfoFilled', 'Warning', 'SuccessFilled',
  'CircleClose', 'Help', 'Magnet', 'Coin', 'Lock', 'SwitchButton', 'ArrowDown', 'Loading',
  'Phone', 'Star', 'Share', 'Files', 'Postcard', 'Reading', 'Notebook', 'Monitor',
  'Search', 'EditPen', 'Remove', 'ZoomIn', 'ZoomOut'
]

const menuNodeCount = computed(() => {
  let count = 0
  const walk = (items: any[]) => {
    for (const item of items) {
      count += 1
      if (Array.isArray(item.children)) walk(item.children)
    }
  }
  walk(treeData.value)
  return count
})

onMounted(() => {
  loadUsers()
})

// 打开图标库时加载离线图标信息（public/image 下的 index.json）
watch(showIconPicker, (v) => { if (v) loadImageIcons() })

async function loadImageIcons() {
  if (imageIcons.value.length) return
  const list: { group: string; name: string; path: string }[] = []
  try {
    const el = await fetch('/image/element-icons/index.json', { cache: 'no-cache' }).then(r => r.json())
    for (const it of el) list.push({ group: 'Element Plus 图标', name: it.name, path: 'element-icons/' + it.file })
  } catch (_e) { /* 图标库未生成时忽略 */ }
  try {
    const ds = await fetch('/image/ds-types/index.json', { cache: 'no-cache' }).then(r => r.json())
    for (const it of ds) list.push({ group: '数据源类型图标', name: it.name, path: 'ds-types/' + it.file })
  } catch (_e) { /* 图标库未生成时忽略 */ }
  imageIcons.value = list
}

/** 按分组+关键字过滤图片图标 */
const imageIconGroups = computed(() => {
  const kw = iconKeyword.value.trim().toLowerCase()
  const groups: { group: string; items: any[] }[] = []
  for (const it of imageIcons.value) {
    if (kw && !it.name.toLowerCase().includes(kw) && !it.path.toLowerCase().includes(kw)) continue
    let g = groups.find(x => x.group === it.group)
    if (!g) { g = { group: it.group, items: [] }; groups.push(g) }
    g.items.push(it)
  }
  return groups
})

/** 判断 icon 值是否为图片路径（否则视为 Element Plus 组件名，保持兼容） */
function isImageIcon(icon?: string) {
  return !!icon && (icon.includes('/') || /\.(svg|png|ico|webp)$/i.test(icon))
}

/** 将存储的图标路径转为可访问的 src */
function iconSrc(icon: string) {
  if (!icon) return ''
  return icon.startsWith('/') ? icon : '/image/' + icon
}

function selectImageIcon(path: string) {
  menuForm.value.icon = path
  showIconPicker.value = false
}

async function loadMenuTree() {
  loading.value = true
  menuError.value = ''
  try {
    const res: any = await configApi.getTree(MENU_CONFIG_TYPE)
    treeData.value = res.data || []
    flatMap.clear()
    function walk(list: any[]) {
      for (const item of list) {
        flatMap.set(item.id, item)
        if (item.children?.length) walk(item.children)
      }
    }
    walk(treeData.value)
  } catch (e: any) {
    treeData.value = []
    menuError.value = `菜单数据加载失败：${e?.message || '请稍后重试'}`
  } finally {
    loading.value = false
  }
}

function openAddDialog(parent: any) {
  const siblings = parent ? (parent.children || []) : treeData.value
  const maxSort = Math.max(0, ...siblings.map((i: any) => i.sortOrder || 0))
  menuForm.value = {
    configType: MENU_CONFIG_TYPE,
    configCode: '',
    configName: '',
    configValue: '',
    description: '',
    icon: 'Folder',
    sortOrder: maxSort + 10,
    status: 1,
    _enabled: true,
    parentId: parent?.id || null,
    _parentName: parent?.configName || ''
  }
  isEditMenu.value = false
  showMenuForm.value = true
}

function openEditDialog(row: any) {
  menuForm.value = { ...row, _enabled: row.status !== 0, _parentName: row.parentId ? flatMap.get(row.parentId)?.configName : '' }
  isEditMenu.value = true
  showMenuForm.value = true
}

async function submitMenuForm() {
  if (!menuForm.value.configCode || !menuForm.value.configName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  saving.value = true
  try {
    const data = { ...menuForm.value, status: menuForm.value._enabled ? 1 : 0 }
    if (isEditMenu.value) {
      await configApi.update(data)
      ElMessage.success('更新成功')
    } else {
      await configApi.create(data)
      ElMessage.success('创建成功')
    }
    showMenuForm.value = false
    await loadMenuTree()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  if (row.children?.length > 0) {
    ElMessage.warning(`该节点下有 ${row.children.length} 个子节点，请先删除子节点`)
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除「${row.configName}」？`, '提示', { type: 'warning' })
    await configApi.delete(row.id)
    ElMessage.success('删除成功')
    await loadMenuTree()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  }
}

function selectIcon(icon: string) {
  menuForm.value.icon = icon
  showIconPicker.value = false
}

// ==================== 字典维护（sys_dict_type / sys_dict_data） ====================

const dictTypes = ref<any[]>([])
const dictTypeKeyword = ref('')
const dictTypePage = ref(1)
const dictTypePageSize = ref(10)
const dictTypeTotal = ref(0)
const dictTypeError = ref('')
const currentDictType = ref<any>(null)
const dictDataList = ref<any[]>([])
const dictDataPage = ref(1)
const dictDataPageSize = ref(10)
const dictDataTotal = ref(0)
const dictDataLoading = ref(false)
const dictDataError = ref('')
const showDictTypeForm = ref(false)
const isEditDictType = ref(false)
const dictTypeForm = ref<any>({})
const showDictDataForm = ref(false)
const isEditDictData = ref(false)
const dictDataForm = ref<any>({})

// 字典数据内容列宽（按当前页内容动态计算，超长由 show-overflow-tooltip 兜底）
const dictLabelWidth = ref(120)
const dictValueWidth = ref(100)
const dictExtraWidth = ref(80)

/** 估算文本渲染宽度（汉字/全角按 14px，ASCII 按 8.5px，另加单元格左右 padding） */
function estimateTextWidth(text: any) {
  if (text == null) return 0
  let w = 0
  for (const ch of String(text)) {
    w += ch.charCodeAt(0) > 255 ? 14 : 8.5
  }
  return Math.ceil(w) + 28
}

/** 根据当前页数据与表头宽度计算内容列宽（设上下限，内容超长由 tooltip 展示全文） */
function calcDictColWidths() {
  let label = estimateTextWidth('标签')
  let value = estimateTextWidth('值')
  let extra = estimateTextWidth('扩展值')
  for (const d of dictDataList.value) {
    label = Math.max(label, estimateTextWidth(d.dictLabel))
    value = Math.max(value, estimateTextWidth(d.dictValue))
    extra = Math.max(extra, estimateTextWidth(d.dictExtra))
  }
  dictLabelWidth.value = Math.min(360, Math.max(110, label))
  dictValueWidth.value = Math.min(340, Math.max(90, value))
  dictExtraWidth.value = Math.min(260, Math.max(80, extra))
}

/** 搜索字典类型：重置到第1页后按关键词加载（按编码/名称模糊匹配） */
function searchDictTypes() {
  dictTypePage.value = 1
  loadDictTypesPage()
}

/** 分页加载字典类型列表（含禁用，支持关键词搜索） */
async function loadDictTypesPage() {
  dictTypeError.value = ''
  try {
    const res: any = await catalogApi.getDictTypesPage(dictTypeKeyword.value, dictTypePage.value, dictTypePageSize.value)
    dictTypes.value = res.data?.records || []
    dictTypeTotal.value = res.data?.total || 0
    // 首次进入或当前选中项已不在列表时，默认选中第一项，右侧直接出数据
    if (dictTypes.value.length && (!currentDictType.value || !dictTypes.value.some((x: any) => x.id === currentDictType.value.id))) {
      selectDictType(dictTypes.value[0])
    }
    if (!dictTypes.value.length) {
      currentDictType.value = null
      dictDataList.value = []
      dictDataTotal.value = 0
    }
  } catch (e: any) {
    dictTypes.value = []
    dictTypeTotal.value = 0
    currentDictType.value = null
    dictDataList.value = []
    dictDataTotal.value = 0
    dictTypeError.value = `字典类型加载失败：${e?.message || '请稍后重试'}`
  }
}

/** 分页加载当前字典类型的数据（含禁用） */
async function loadDictDataPage() {
  if (!currentDictType.value) {
    dictDataList.value = []
    dictDataTotal.value = 0
    return
  }
  dictDataLoading.value = true
  dictDataError.value = ''
  try {
    const res: any = await catalogApi.getDictDataPage(currentDictType.value.dictTypeCode, dictDataPage.value, dictDataPageSize.value)
    dictDataList.value = res.data?.records || []
    dictDataTotal.value = res.data?.total || 0
    // 按内容动态适配列宽（短内容列收窄、长内容列加宽）
    calcDictColWidths()
  } catch (e: any) {
    dictDataList.value = []
    dictDataTotal.value = 0
    dictDataError.value = `字典数据加载失败：${e?.message || '请稍后重试'}`
  } finally {
    dictDataLoading.value = false
  }
}

/** 选择左侧字典类型：右侧列表加载该类型的数据 */
function selectDictType(row: any) {
  if (!row) return
  currentDictType.value = row
  dictDataPage.value = 1
  loadDictDataPage()
}

function openDictTypeForm(row: any) {
  isEditDictType.value = !!row
  dictTypeForm.value = row
    ? { ...row, _enabled: row.status !== 0 }
    : { dictTypeCode: '', dictTypeName: '', remark: '', status: 1, _enabled: true }
  showDictTypeForm.value = true
}

async function submitDictTypeForm() {
  if (!dictTypeForm.value.dictTypeCode || !dictTypeForm.value.dictTypeName) {
    ElMessage.warning('请填写类型编码和名称')
    return
  }
  try {
    const data = { ...dictTypeForm.value, status: dictTypeForm.value._enabled ? 1 : 0 }
    if (isEditDictType.value) {
      await catalogApi.updateDictType(data)
      ElMessage.success('更新成功')
    } else {
      await catalogApi.createDictType(data)
      ElMessage.success('创建成功')
    }
    showDictTypeForm.value = false
    await loadDictTypesPage()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function removeDictType(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除字典类型「${row.dictTypeName}」及其全部数据？`, '提示', { type: 'warning' })
    await catalogApi.deleteDictType(row.id)
    ElMessage.success('删除成功')
    currentDictType.value = null
    // 删除后当前页为空且非第一页时回退一页
    if (dictTypes.value.length === 1 && dictTypePage.value > 1) {
      dictTypePage.value -= 1
    }
    await loadDictTypesPage()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  }
}

function openDictDataForm(row: any) {
  if (!currentDictType.value) return
  isEditDictData.value = !!row
  dictDataForm.value = row
    ? { ...row, _enabled: row.status !== 0 }
    : {
        dictTypeId: currentDictType.value.id,
        dictLabel: '',
        dictValue: '',
        dictExtra: '',
        // 默认排序号 = 当前已加载数据的最大排序号 + 10
        sortOrder: Math.max(0, ...dictDataList.value.map((i: any) => i.sortOrder || 0)) + 10,
        isDefault: false,
        remarks: '',
        status: 1,
        _enabled: true
      }
  showDictDataForm.value = true
}

async function submitDictDataForm() {
  if (!dictDataForm.value.dictLabel || !dictDataForm.value.dictValue) {
    ElMessage.warning('请填写字典标签和值')
    return
  }
  try {
    const data = { ...dictDataForm.value, status: dictDataForm.value._enabled ? 1 : 0 }
    if (isEditDictData.value) {
      await catalogApi.updateDictData(data)
      ElMessage.success('更新成功')
    } else {
      await catalogApi.createDictData(data)
      ElMessage.success('创建成功')
    }
    showDictDataForm.value = false
    await loadDictDataPage()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function removeDictData(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除字典数据「${row.dictLabel}」？`, '提示', { type: 'warning' })
    await catalogApi.deleteDictData(row.id)
    ElMessage.success('删除成功')
    // 删除后当前页为空且非第一页时回退一页
    if (dictDataList.value.length === 1 && dictDataPage.value > 1) {
      dictDataPage.value -= 1
    }
    await loadDictDataPage()
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  }
}

// ==================== 页签切换与刷新 ====================

function handleTabChange(name: string | number) {
  if (name === 'users' && !userLoaded.value) {
    loadUsers()
  } else if (name === 'roles' && !roleLoaded.value) {
    loadRoles()
  } else if (name === 'authorization') {
    ensureAuthOptions()
  } else if (name === 'menus' && !treeData.value.length) {
    loadMenuTree()
  } else if (name === 'dict' && !dictTypes.value.length) {
    loadDictTypesPage()
  }
}

async function refreshActiveTab() {
  refreshing.value = true
  try {
    if (activeTab.value === 'users') {
      await loadUsers()
    } else if (activeTab.value === 'roles') {
      await loadRoles()
    } else if (activeTab.value === 'authorization') {
      authUsers.value = []
      authRoles.value = []
      authMenuTree.value = []
      authOptionsError.value = ''
      authMenuError.value = ''
      await ensureAuthOptions(true)
    } else if (activeTab.value === 'menus') {
      await loadMenuTree()
    } else if (activeTab.value === 'dict') {
      await loadDictTypesPage()
      if (currentDictType.value) await loadDictDataPage()
    }
  } finally {
    refreshing.value = false
  }
}
</script>

<style scoped>
.config-page { min-height: calc(100vh - 64px); }
.config-panel { padding: 10px 20px 20px; }
.config-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
.config-tabs :deep(.el-tabs__content) { overflow: visible; }

/* 工具栏 */
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.toolbar-title { font-weight: 600; font-size: 14px; color: var(--med-text); }
.toolbar-sub { font-size: 12px; color: var(--med-text-light); }
.toolbar-total { margin-left: auto; color: var(--med-text-light); font-size: 12px; }
.toolbar .el-button + .el-button { margin-left: 0; }
.data-error { margin-bottom: 12px; }
.muted { color: var(--med-text-light); }

/* 用户 / 角色操作列紧凑排列 */
.el-button.table-op { padding: 4px 6px; margin-left: 0; white-space: nowrap; }
.el-button.table-op + .el-button.table-op { margin-left: 2px; }

/* 菜单树 */
.menu-tree-wrap { min-height: 360px; max-height: calc(100vh - 330px); overflow-y: auto; padding: 4px; }
.tree-node { display: flex; align-items: center; gap: 4px; font-size: 13px; width: 100%; }
.node-code { color: #909399; font-size: 11px; margin-left: 4px; }
.node-actions { margin-left: auto; display: none; gap: 2px; }
.el-tree-node__content:hover .node-actions { display: flex; }
.tree-icon-img { width: 16px; height: 16px; object-fit: contain; }

/* 授权管理 */
.auth-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
.auth-grid { display: grid; grid-template-columns: 340px minmax(0, 1fr); gap: 18px; align-items: start; }
.auth-col { border: 1px solid var(--med-border-light); border-radius: 12px; padding: 16px; min-height: 240px; background: #fff; }
.auth-hint { color: var(--med-text-light); font-size: 12px; line-height: 1.6; margin: 14px 0 0; }
.auth-check-wrap { min-height: 120px; }
.auth-check-list { display: flex; flex-direction: column; gap: 10px; }
.auth-role-code { color: var(--med-text-light); font-size: 11px; margin-left: 8px; }
.auth-tree-wrap { min-height: 220px; max-height: calc(100vh - 430px); overflow: auto; }
.auth-actions { display: flex; justify-content: flex-end; margin-top: 16px; }

/* 字典维护：左侧类型 / 右侧数据 */
.dict-split { display: grid; grid-template-columns: 40% 60%; height: calc(100vh - 330px); min-height: 460px; }
.dict-left { display: flex; flex-direction: column; min-height: 0; padding-right: 16px; border-right: 1px solid var(--med-border); }
.dict-right { display: flex; flex-direction: column; min-height: 0; padding-left: 18px; }
.dict-toolbar { display: flex; align-items: center; gap: 8px; padding: 2px 0 10px; flex-wrap: wrap; }
.dict-toolbar b { font-size: 14px; color: var(--med-text); }
.dict-toolbar span { font-size: 11.5px; color: var(--med-text-light); margin-left: 4px; }
.dict-type-list { flex: 1; min-height: 0; overflow-y: auto; }
.dict-type-item { display: flex; align-items: center; gap: 8px; padding: 9px 10px; border-radius: 9px; cursor: pointer; border: 1px solid transparent; }
.dict-type-item:hover { background: #f5f7fb; }
.dict-type-item.active { background: var(--med-primary-lighter); border-color: #d6e6ff; }
.dict-type-main { flex: 1; min-width: 0; }
.dict-type-main b { display: block; font-size: 12.5px; font-weight: 600; color: var(--med-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dict-type-main span { font-size: 10.5px; color: var(--med-text-light); font-family: ui-monospace, Menlo, monospace; }
.dict-type-ops { display: none; gap: 0; }
.dict-type-item:hover .dict-type-ops { display: flex; }
.dict-pager { display: flex; justify-content: flex-end; padding-top: 10px; }
.dict-data-table { flex: 1; min-height: 0; overflow: hidden; }

/* 操作列文字按钮紧凑排列 */
.el-button.dict-op-btn { padding: 4px 6px; margin-left: 0; white-space: nowrap; }
.el-button.dict-op-btn + .el-button.dict-op-btn { margin-left: 2px; }

/* 图标选择器 */
.icon-field { display: flex; gap: 4px; width: 100%; }
.icon-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; max-height: 360px; overflow-y: auto; }
.icon-item { display: flex; flex-direction: column; align-items: center; padding: 8px; cursor: pointer; border-radius: 6px; font-size: 11px; color: #606266; }
.icon-group { margin-bottom: 12px; }
.icon-group-title { font-size: 12px; color: #909399; margin: 4px 0 8px; font-weight: 600; }
.icon-img { width: 20px; height: 20px; object-fit: contain; }
.icon-name { max-width: 72px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.icon-item:hover { background: #ecf5ff; }
.icon-item.active { background: #409eff; color: #fff; }

@media (max-width: 1100px) {
  .auth-grid { grid-template-columns: 1fr; }
  .dict-split { grid-template-columns: 1fr; height: auto; }
  .dict-left { border-right: none; padding-right: 0; margin-bottom: 16px; }
  .dict-right { padding-left: 0; }
}
</style>
