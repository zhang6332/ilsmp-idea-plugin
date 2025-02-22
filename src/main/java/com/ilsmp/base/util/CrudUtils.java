/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ilsmp.base.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.mars05.crud.hub.common.dto.DataSourceDTO;
import com.github.mars05.crud.hub.common.repository.DataSourceRepository;
import com.github.mars05.crud.hub.common.service.DataSourceService;
import com.github.mars05.crud.hub.common.service.ProjectService;
import com.github.mars05.crud.hub.common.util.BeanUtils;
import com.ilsmp.base.dao.mapper.DataSourceMapper;
import com.ilsmp.base.dao.mapper.ProjectTemplateMapper;
import com.ilsmp.base.dao.model.DataSourceDO;
import com.ilsmp.base.service.ProjectTemplateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.DisposeAwareRunnable;

public class CrudUtils {
    private static final Map<Class<?>, Object> BEAN_MAP = new ConcurrentHashMap<>();
    public static final Charset UTF_8 = StandardCharsets.UTF_8;

    static {
        BEAN_MAP.put(DataSourceMapper.class, new DataSourceMapper());
        BEAN_MAP.put(ProjectTemplateMapper.class, new ProjectTemplateMapper());

        BEAN_MAP.put(DataSourceRepository.class, new DataSourceRepository() {
            private final DataSourceMapper dataSourceMapper = CrudUtils.getBean(DataSourceMapper.class);

            @Override
            public List<DataSourceDTO> selectList() {
                return BeanUtils.convertList(dataSourceMapper.selectList(), DataSourceDTO.class);
            }

            @Override
            public void insert(DataSourceDTO dataSourceDTO) {
                dataSourceMapper.insert(BeanUtils.convertBean(dataSourceDTO, DataSourceDO.class));
            }

            @Override
            public void updateById(DataSourceDTO dataSourceDTO) {
                dataSourceMapper.updateById(BeanUtils.convertBean(dataSourceDTO, DataSourceDO.class));
            }

            @Override
            public DataSourceDTO selectById(Long id) {
                return BeanUtils.convertBean(dataSourceMapper.selectById(id), DataSourceDTO.class);
            }

            @Override
            public void deleteById(Long id) {
                dataSourceMapper.deleteById(id);
            }
        });


        BEAN_MAP.put(DataSourceService.class, new DataSourceService(getBean(DataSourceRepository.class)));
        BEAN_MAP.put(ProjectService.class, new ProjectService());
        BEAN_MAP.put(ProjectTemplateService.class, new ProjectTemplateService());

    }

    public static <T> T getBean(Class<T> clazz) {
        Object obj = BEAN_MAP.get(clazz);
        if (obj == null) {
            throw new IllegalStateException("[" + clazz.getName() + "]实例未找到");
        }
        return (T) obj;
    }

    public static void invokeAndWait(Project p, Runnable r) {
        invokeAndWait(p, ModalityState.defaultModalityState(), r);
    }

    public static void invokeAndWait(Project p, ModalityState state, Runnable r) {
        if (isNoBackgroundMode()) {
            r.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(DisposeAwareRunnable.create(r, p), state);
        }
    }

    public static void invokeLater(Project p, Runnable r) {
        invokeLater(p, ModalityState.defaultModalityState(), r);
    }

    public static void invokeLater(Project p, ModalityState state, Runnable r) {
        if (isNoBackgroundMode()) {
            r.run();
        } else {
            ApplicationManager.getApplication().invokeLater(DisposeAwareRunnable.create(r, p), state);
        }

    }

    public static void runWriteCommandAction(final Project project, final Runnable r) {
        runWhenInitialized(project, () -> WriteCommandAction.writeCommandAction(project).run(r::run));
    }

    public static void runWhenInitialized(final Project project, final Runnable r) {
        if (project.isDisposed()) {
            return;
        }
        if (isNoBackgroundMode()) {
            r.run();
            return;
        }
        if (!project.isInitialized()) {
            StartupManager.getInstance(project).registerPostStartupActivity(DisposeAwareRunnable.create(r, project));
            return;
        }
        runDumbAware(project, r);
    }

    public static boolean isNoBackgroundMode() {
        return (ApplicationManager.getApplication().isUnitTestMode()
                || ApplicationManager.getApplication().isHeadlessEnvironment());
    }

    public static void runDumbAware(final Project project, final Runnable r) {
        if (DumbService.isDumbAware(r)) {
            r.run();
        } else {
            DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(r, project));
        }
    }

    private static List<VirtualFile> virtualFiles = new ArrayList<>();

    public static void addWaitOptimizeFile(VirtualFile virtualFile) {
        virtualFiles.add(virtualFile);
    }

    public static void doOptimize(Project project) {
        DumbService.getInstance(project).runWhenSmart((DumbAwareRunnable) () -> WriteCommandAction.writeCommandAction(project).run(() -> {
            for (VirtualFile virtualFile : virtualFiles) {
                try {
                    PsiJavaFile javaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(virtualFile);
                    if (javaFile != null) {
                        CodeStyleManager.getInstance(project).reformat(javaFile);
                        JavaCodeStyleManager.getInstance(project).optimizeImports(javaFile);
                        JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            virtualFiles.clear();
        }));
    }

    public static void runInBackground(Task.Backgroundable task) {
        task.queue();
    }


}
