package com.sparta.trelloproject.common.aop;

import com.sparta.trelloproject.common.annotation.AuthUser;
import com.sparta.trelloproject.common.exception.ForbiddenException;
import com.sparta.trelloproject.common.exception.ResponseCode;
import com.sparta.trelloproject.domain.workspace.entity.UserWorkspace;
import com.sparta.trelloproject.domain.workspace.enums.WorkSpaceUserRole;
import com.sparta.trelloproject.domain.workspace.repository.UserWorkSpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Aspect
@Component
public class WorkspacePermissionAspect {

    private final UserWorkSpaceRepository userWorkSpaceRepository;

    @Pointcut("@annotation(com.sparta.trelloproject.common.annotation.WorkspaceAdminPermission)")
    public void workspaceAdminPermissionPointcut() {}

    @Pointcut("@annotation(com.sparta.trelloproject.common.annotation.WorkspaceEditPermission)")
    public void workspaceEditPermissionPointcut() {}

    @Pointcut("@annotation(com.sparta.trelloproject.common.annotation.WorkspaceReadPermission)")
    public void workspaceReadPermissionPointcut() {}


    @Around("workspaceAdminPermissionPointcut()")
    public Object adminPermission(ProceedingJoinPoint joinPoint) throws Throwable {

        WorkSpaceUserRole workSpaceUserRole = getWorkSpaceUserRole(joinPoint);

        if (!workSpaceUserRole.equals(WorkSpaceUserRole.ROLE_WORKSPACE_ADMIN)) {
            throw new ForbiddenException(ResponseCode.FORBIDDEN);
        }

        return joinPoint.proceed();
    }

    @Around("workspaceEditPermissionPointcut()")
    public Object editPermission(ProceedingJoinPoint joinPoint) throws Throwable {

        WorkSpaceUserRole workSpaceUserRole = getWorkSpaceUserRole(joinPoint);

        if (!workSpaceUserRole.equals(WorkSpaceUserRole.ROLE_EDIT_USER)) {
            throw new ForbiddenException(ResponseCode.FORBIDDEN);
        }

        return joinPoint.proceed();
    }

    @Around("workspaceReadPermissionPointcut()")
    public Object readPermission(ProceedingJoinPoint joinPoint) throws Throwable {

        WorkSpaceUserRole workSpaceUserRole = getWorkSpaceUserRole(joinPoint);

        if (!workSpaceUserRole.equals(WorkSpaceUserRole.ROLE_READ_USER)) {
            throw new ForbiddenException(ResponseCode.FORBIDDEN);
        }

        return joinPoint.proceed();
    }

    private WorkSpaceUserRole getWorkSpaceUserRole(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        Parameter[] parameters = method.getParameters();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Long userId = null;
        Long workspaceId = null;

        if (authentication != null && authentication.isAuthenticated()) {
            AuthUser authUser = (AuthUser) authentication.getPrincipal();
            userId = authUser.getUserId(); // userId를 가져옴
        }

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(PathVariable.class)) {
                workspaceId = (Long) args[i];
                break;
            }
        }

        if (Objects.isNull(workspaceId)) {
            for (Object arg : args) {
                try {
                    // 객체의 클래스에서 'workspaceId' 필드 찾기
                    Field field = arg.getClass().getDeclaredField("workspaceId");
                    field.setAccessible(true); // private 필드에도 접근 가능하게 설정
                    workspaceId = (Long) field.get(arg); // 필드 값 가져오기
                    break; // workspaceId를 찾으면 반복을 종료
                } catch (NoSuchFieldException e) {
                    // 필드가 없을 경우 예외를 처리하고, 다른 객체를 확인
                    log.warn("Field 'workspaceId' not found in object of class: " + arg.getClass().getName());
                } catch (IllegalAccessException e) {
                    log.error("Unable to access the field 'workspaceId'", e);
                }
            }
        }

        UserWorkspace userWorkSpace = userWorkSpaceRepository.findByWorkspaceIdAndUserId(workspaceId , userId);

        return userWorkSpace.getWorkSpaceUserRole();
    }
}