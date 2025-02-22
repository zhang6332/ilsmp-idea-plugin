package com.ilsmp.base.wizard;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.mars05.crud.hub.common.dto.ProjectGenerateReqDTO;
import com.github.mars05.crud.hub.common.dto.ProjectRespDTO;
import com.github.mars05.crud.hub.common.dto.ProjectTemplateDTO;
import com.github.mars05.crud.hub.common.enums.ProjectTypeEnum;
import com.github.mars05.crud.hub.common.exception.BizException;
import com.github.mars05.crud.hub.common.service.ProjectService;
import com.github.mars05.crud.hub.common.util.BeanUtils;
import com.google.common.base.Preconditions;
import com.ilsmp.base.icon.CrudIcons;
import com.ilsmp.base.service.ProjectTemplateService;
import com.ilsmp.base.setting.CrudSettings;
import com.ilsmp.base.step.CrudConnStep;
import com.ilsmp.base.step.CrudDbStep;
import com.ilsmp.base.step.CrudSchemaStep;
import com.ilsmp.base.step.CrudTableStep;
import com.ilsmp.base.step.DataSelectStep;
import com.ilsmp.base.step.DdlStep;
import com.ilsmp.base.step.MyTemplateStep;
import com.ilsmp.base.ui.JavaView;
import com.ilsmp.base.ui.MavenView;
import com.ilsmp.base.util.CrudUtils;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;

public class CrudModuleBuilder extends ModuleBuilder {
    JavaView javaView = new JavaView();
    MavenView mavenView = new MavenView();

    private final ProjectTemplateService projectTemplateService = CrudUtils.getBean(ProjectTemplateService.class);
    private final ProjectService projectService = CrudUtils.getBean(ProjectService.class);

    public CrudModuleBuilder() {
    }

    @Nullable
    @Override
    public String getBuilderId() {
        return getClass().getName();
    }

    @Override
    public Icon getNodeIcon() {
        return CrudIcons.LOGO;
    }

    @Override
    public String getPresentableName() {
        return "Base";
    }

    @Override
    public String getDescription() {
        return "Base";
    }

    @Override
    public ModuleType getModuleType() {
        return StdModuleTypes.JAVA;
    }

    @Override
    public boolean isSuitableSdkType(SdkTypeId sdk) {
        return sdk == JavaSdk.getInstance();
    }

    @Nullable
    @Override
    public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
        return new MyTemplateStep();
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
        CrudSettings.newGenerate();
        return new ModuleWizardStep[]{
                new DataSelectStep(),
                new DdlStep(),
                new CrudConnStep(),
                new CrudDbStep(),
                new CrudSchemaStep(),
                new CrudTableStep()
        };
    }

    @Nullable
    @Override
    public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
        ProjectTemplateDTO projectTemplate = CrudSettings.currentGenerate().getProjectTemplate();
        JTextField moduleNameField = settingsStep.getModuleNameField();
        JTextField groupIdTextField = mavenView.getGroupIdTextField();
        JTextField artifactIdTextField = mavenView.getArtifactIdTextField();
        JTextField basePackageTextField = ProjectTypeEnum.JAVA.getCode() == projectTemplate.getProjectType() ?
                javaView.getBasePackageTextField() : mavenView.getBasePackageTextField();

        AtomicBoolean moduleInput = new AtomicBoolean(false);
        AtomicBoolean artifactIdInput = new AtomicBoolean(false);
        AtomicBoolean syncInput = new AtomicBoolean(false);

        if (ProjectTypeEnum.JAVA.getCode() == projectTemplate.getProjectType()) {
            settingsStep.addSettingsComponent(javaView.getComponent());
        }
        if (ProjectTypeEnum.MAVEN.getCode() == projectTemplate.getProjectType()) {
            settingsStep.addSettingsComponent(mavenView.getComponent());
            if (moduleNameField != null) {
                artifactIdTextField.setText(moduleNameField.getText());
                basePackageTextField.setText(groupIdTextField.getText()
                        + "." + artifactIdTextField.getText());
            }
            groupIdTextField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {

                }

                @Override
                public void removeUpdate(DocumentEvent e) {

                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    basePackageTextField.setText(groupIdTextField.getText()
                            + "." + artifactIdTextField.getText());
                }
            });
            artifactIdTextField.getDocument().addDocumentListener(new DocumentListener() {

                @Override
                public void insertUpdate(DocumentEvent e) {

                }

                @Override
                public void removeUpdate(DocumentEvent e) {

                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    basePackageTextField.setText(groupIdTextField.getText()
                            + "." + artifactIdTextField.getText());
                    if (moduleNameField != null) {
                        if (!syncInput.get()) {
                            artifactIdInput.set(true);
                            if (!moduleInput.get()) {
                                syncInput.set(true);
                                moduleNameField.setText(artifactIdTextField.getText());
                                syncInput.set(false);
                            }
                        }
                    }
                }
            });
        }

        if (moduleNameField != null) {
            moduleNameField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {

                }

                @Override
                public void removeUpdate(DocumentEvent e) {

                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    if (ProjectTypeEnum.MAVEN.getCode() == projectTemplate.getProjectType()) {
                        if (!syncInput.get()) {
                            moduleInput.set(true);
                            if (!artifactIdInput.get()) {
                                syncInput.set(true);
                                artifactIdTextField.setText(moduleNameField.getText());
                                syncInput.set(false);
                            }
                        }
                    }
                }
            });
        }
        return super.modifySettingsStep(settingsStep);
    }

    @Override
    public boolean validateModuleName(@NotNull String moduleName) throws ConfigurationException {
        ProjectTemplateDTO projectTemplate = CrudSettings.currentGenerate().getProjectTemplate();
        try {
            if (projectTemplate.getProjectType() == ProjectTypeEnum.JAVA.getCode()) {
                String basePackage = javaView.getBasePackageTextField().getText();
                if (StringUtils.isBlank(basePackage)) {
                    throw new BizException("basePackage不能为空");
                }
                CrudSettings.currentGenerate().setBasePackage(basePackage);
            } else if (projectTemplate.getProjectType() == ProjectTypeEnum.MAVEN.getCode()) {
                String groupId = mavenView.getGroupIdTextField().getText();
                String artifactId = mavenView.getArtifactIdTextField().getText();
                String version = mavenView.getVersionTextField().getText();
                String basePackage = mavenView.getBasePackageTextField().getText();
                if (StringUtils.isBlank(groupId)) {
                    throw new BizException("groupId不能为空");
                }
                if (StringUtils.isBlank(artifactId)) {
                    throw new BizException("artifactId不能为空");
                }
                if (StringUtils.isBlank(version)) {
                    throw new BizException("version不能为空");
                }
                if (StringUtils.isBlank(basePackage)) {
                    throw new BizException("basePackage不能为空");
                }
                Preconditions.checkArgument(com.github.mars05.crud.hub.common.util.StringUtils.isPackageName(basePackage), "basePackage格式错误");
                CrudSettings.currentGenerate().setGroupId(groupId);
                CrudSettings.currentGenerate().setArtifactId(artifactId);
                CrudSettings.currentGenerate().setVersion(version);
                CrudSettings.currentGenerate().setBasePackage(basePackage);
            }
        } catch (Exception e) {
            throw new ConfigurationException(e.getMessage(), "表选择失败");
        }
        return super.validateModuleName(moduleName);
    }

    @Override
    public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
        if (this.myJdk != null) {
            rootModel.setSdk(this.myJdk);
        } else {
            rootModel.inheritSdk();
        }
        Project project = rootModel.getProject();
        VirtualFile root = createAndGetContentEntry();
        rootModel.addContentEntry(root);

        CrudSettings.currentGenerate().setProjectName(project.getName());
        CrudUtils.runWriteCommandAction(project, () -> {
            ProjectGenerateReqDTO projectGenerateReqDTO = BeanUtils.convertBean(CrudSettings.currentGenerate(), ProjectGenerateReqDTO.class);
            ProjectRespDTO respDTO = projectService.generateProject(projectGenerateReqDTO);
            projectService.processFileToDisk(root.getCanonicalPath(), respDTO.getFiles());
            VirtualFileManager.getInstance().asyncRefresh(() -> {
            });
        });
    }

    private VirtualFile createAndGetContentEntry() {
        String path = FileUtil.toSystemIndependentName(getContentEntryPath());
        new File(path).mkdirs();
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    }
}
