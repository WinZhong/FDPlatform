package com.zsinda.fdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zsinda.fdp.dto.UserDto;
import com.zsinda.fdp.dto.UserInfo;
import com.zsinda.fdp.entity.SysConfig;
import com.zsinda.fdp.entity.SysRole;
import com.zsinda.fdp.entity.SysUser;
import com.zsinda.fdp.exception.SysException;
import com.zsinda.fdp.mapper.SysUserMapper;
import com.zsinda.fdp.service.*;
import com.zsinda.fdp.vo.MenuVO;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zsinda.fdp.constant.SysConfigConstants.SYS_USER_DEFAULT_PASSWORD;
import static com.zsinda.fdp.constant.SysConfigConstants.SYS_USER_DEFAULT_ROLE;

@Service
@AllArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysRoleService sysRoleService;

    private final SysUserRoleService sysUserRoleService;

    private final SysMenuService sysMenuService;

    private final SysConfigService sysConfigService;

    private final SysDeptService sysDeptService;

    private final SysUserDeptService sysUserDeptService;

    @Override
    public UserInfo findUserInfo(SysUser sysUser) {
        UserInfo userInfo = new UserInfo();
        userInfo.setSysUser(sysUser);
        //查询该用户的角色
        List<SysRole> sysRoles = sysRoleService.findRoleById(sysUser.getUserId());
        List<Integer> roleIds = sysRoles.stream().map(SysRole::getRoleId)
                .collect(Collectors.toList());

        userInfo.setRoles(ArrayUtil.toArray(roleIds, Integer.class));

        //设置权限列表（menu.permission）
        Set<String> permissions = new HashSet<>();

        roleIds.forEach(roleId -> {
            List<String> permissionList = sysMenuService.findMenuByRoleId(roleId)
                    .stream()
                    .filter(menu -> StringUtils.isNotEmpty(menu.getPermission()))
                    .map(MenuVO::getPermission)
                    .collect(Collectors.toList());
            permissions.addAll(permissionList);
        });
        userInfo.setPermissions(ArrayUtil.toArray(permissions, String.class));
        return userInfo;
    }

    @Override
    public IPage userPage(Page page, UserDto userDto) {
        return baseMapper.userPage(page, userDto);
    }

    @Override
    @Transactional
    public Boolean updateUser(SysUser sysUser) {
        return updateById(sysUser);
    }

    @Override
    @Transactional
    public Boolean deleteUser(Integer id) {
        SysUser sysUser = checkUserId(id);
        if (sysUser.getDelFlag() == 0) {
            sysUser.setDelFlag(1);
        } else {
            sysUser.setDelFlag(0);
        }
        return updateById(sysUser);
    }

    @Override
    @Transactional
    public Boolean lock(Integer id) {
        SysUser sysUser = checkUserId(id);
        if (sysUser.getLockFlag() == 0) {
            sysUser.setLockFlag(1);
        } else {
            sysUser.setLockFlag(0);
        }
        return updateById(sysUser);
    }

    @Override
    public String importUser(List<SysUser> userList, boolean updateSupport) {
        if (CollectionUtils.isEmpty(userList)) {
            throw SysException.sysFail("导入用户数据不能为空!");
        }
        // 系统默认密码配置
        SysConfig sysConfigPassWord = sysConfigService.getSysConfigByKey(SYS_USER_DEFAULT_PASSWORD);
        // 系统默认角色配置
        SysConfig sysConfigRole = sysConfigService.getSysConfigByKey(SYS_USER_DEFAULT_ROLE);
        // 系统默认部门
        SysConfig sysConfigDept = sysConfigService.getSysConfigByKey(SYS_USER_DEFAULT_ROLE);
        // 所有的部门id
        List<Integer> allDeptIds = sysDeptService.getAllDeptIds();
        // 所有的角色id
        List<Integer> allRoleIds = sysRoleService.getAllRoleIds();
        // 成功总数
        int successNum = 0;
        // 失败总数
        int failureNum = 0;
        // 成功导入信息
        StringBuilder successMsg = new StringBuilder();
        // 失败导入信息
        StringBuilder failureMsg = new StringBuilder();

        for (SysUser sysUser : userList) {
            try {
                // 用户部门
                List<Integer> deptIds = getDeptIds(allDeptIds, sysUser.getDeptIds(),sysConfigDept);
                // 用户角色
                List<Integer> roleIds = getRoleIds(allRoleIds, sysUser.getRoleIds(),sysConfigRole);
                SysUser user = getOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, sysUser.getUsername()));
                if (ObjectUtils.isEmpty(user)) {
                    sysUser.setPassword(sysConfigPassWord.getConfigValue());
                    sysUser.setUserId(null);
                    saveUserAndRoleAndDept(sysUser,deptIds,roleIds);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + sysUser.getUsername() + " 导入成功");
                } else if (updateSupport) {
                    // 已存在的用户 是否更新信息
                    // 原id不变，防止导入时用户id不对应
                    sysUser.setUserId(user.getUserId());
                    // 创建时间不变
                    sysUser.setCreateTime(user.getCreateTime());
                    updateUserAndRoleAndDept(sysUser,deptIds,roleIds);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + sysUser.getUsername() + " 更新成功");
                } else {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、账号 " + sysUser.getUsername() + " 已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、账号 " + sysUser.getUsername() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw SysException.sysFail(failureMsg.toString());
        } else {
            successMsg.insert(0, "数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    /**
     * 部门去重
     * 去无效部门
     * 如果为空 补充默认部门
     * @param allDeptIds
     * @param userDeptIds
     * @param sysConfigDept
     * @return
     */
    private List<Integer> getDeptIds(List<Integer> allDeptIds, List<Integer> userDeptIds, SysConfig sysConfigDept) {
        if (CollectionUtil.isNotEmpty(userDeptIds)){
            return userDeptIds.stream()
                    .distinct().filter(id-> allDeptIds.contains(id)).collect(Collectors.toList());
        }else {
            userDeptIds = new ArrayList();
            userDeptIds.add(Integer.valueOf(sysConfigDept.getConfigValue()));
            return userDeptIds;
        }
    }

    /**
     * 角色去重
     * 去无效角色
     * 如果为空 补充默认角色
     * @param allRoleIds
     * @param userRoleIds
     * @param sysConfigRole
     * @return
     */
    private List<Integer> getRoleIds(List<Integer> allRoleIds, List<Integer> userRoleIds, SysConfig sysConfigRole) {
        if (CollectionUtil.isNotEmpty(userRoleIds)){
            return userRoleIds.stream()
                    .distinct().filter(id-> allRoleIds.contains(id)).collect(Collectors.toList());
        }else {
            userRoleIds = new ArrayList();
            userRoleIds.add(Integer.valueOf(sysConfigRole.getConfigValue()));
            return userRoleIds;
        }
    }

    /**
     * 保存用户 角色 和 部门
     * @param sysUser
     * @param deptIds
     * @param roleIds
     */
    private void saveUserAndRoleAndDept(SysUser sysUser, List<Integer> deptIds, List<Integer> roleIds) {
        // 新增用户
        baseMapper.insert(sysUser);
        // 新增用户角色
        sysUserRoleService.saveUserRole(sysUser.getUserId(),roleIds);
        // 新增用户部门
        sysUserDeptService.saveUserDept(sysUser.getUserId(),deptIds);
    }


    private void updateUserAndRoleAndDept(SysUser sysUser, List<Integer> deptIds, List<Integer> roleIds) {
        // 更新用户
        updateUser(sysUser);
        // 更新用户角色
        sysUserRoleService.updateUserRole(sysUser.getUserId(),roleIds);
        // 新增用户部门
        sysUserDeptService.updateUserDept(sysUser.getUserId(),deptIds);
    }

    /**
     * 校验用户id 是否存在
     * @param id
     * @return
     */
    private SysUser checkUserId(Integer id) {
        SysUser sysUser = getOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUserId, id));
        if (sysUser == null) {
            throw SysException.sysFail("没有此用户!");
        }
        return sysUser;
    }


}
