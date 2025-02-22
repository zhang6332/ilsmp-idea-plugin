package com.ilsmp.base.setting;

import javax.swing.*;
import java.awt.*;

import com.github.mars05.crud.hub.common.dto.ProjectTemplateDTO;
import com.github.mars05.crud.hub.common.exception.BizException;
import com.ilsmp.base.rpc.HubClient;
import com.ilsmp.base.rpc.request.MarketplaceGetRequest;
import com.ilsmp.base.rpc.request.MarketplaceListRequest;
import com.ilsmp.base.rpc.request.TokenGetRequest;
import com.ilsmp.base.rpc.response.MarketplaceListResponse;
import com.ilsmp.base.rpc.response.ProjectTemplateResponse;
import com.ilsmp.base.service.ProjectTemplateService;
import com.ilsmp.base.ui.CrudList;
import com.ilsmp.base.ui.ListElement;
import com.ilsmp.base.util.CrudUtils;
import com.ilsmp.base.util.ThreadUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TemplateImportConfigurable implements SearchableConfigurable, Disposable {
    private JPanel myMainPanel;
    private CrudList marketplaceList;
    private JScrollPane myScrollPane;
    private JTextField keywordTextField;
    private JButton searchButton;
    private JButton tokenButton;
    private JPanel loadingPanel;
    private JButton importButton;
    private JScrollPane myInfoScrollPane;
    private JLabel nameLabel;
    private JLabel orgLabel;
    private JLabel timeLabel;
    private JLabel creatorLabel;
    private JLabel descLabel;
    private JPanel infoPanel;
    private JScrollPane myDescScrollPane;

    private final HubClient hubClient = new HubClient();

    private final ProjectTemplateService projectTemplateService = CrudUtils.getBean(ProjectTemplateService.class);

    public TemplateImportConfigurable() {
        searchButton.addActionListener(e -> getList(new MarketplaceListRequest()));
        importButton.addActionListener(e -> {
            if (marketplaceList.getSelectedElement() == null) {
                Messages.showErrorDialog("请选择模板", "错误");
                return;
            }
            try {
                ProjectTemplateResponse response = hubClient.execute(new MarketplaceGetRequest().setId(marketplaceList.getSelectedElement().getId()));
                if (!response.isSuccess()) {
                    throw new BizException(response.getMessage());
                }
                projectTemplateService.create(response.getProjectTemplate());
                Messages.showInfoMessage("导入成功", "提示");
                MyTemplateConfigurable.refreshList();
            } catch (Exception exception) {
                Messages.showErrorDialog(exception.getMessage(), "错误");
            }
        });
        tokenButton.addActionListener(e -> {
            String s = Messages.showInputDialog("令牌", "令牌导入", Messages.getInformationIcon());
            try {
                if (s != null) {
                    ProjectTemplateResponse response = hubClient.execute(new TokenGetRequest().setAccessToken(s));
                    if (!response.isSuccess()) {
                        throw new BizException(response.getMessage());
                    }
                    projectTemplateService.create(response.getProjectTemplate());
                    Messages.showInfoMessage("导入成功", "提示");
                    MyTemplateConfigurable.refreshList();
                }
            } catch (Exception exception) {
                Messages.showErrorDialog(exception.getMessage(), "错误");
            }
        });
        marketplaceList.addListSelectionListener(e -> {
            if (marketplaceList.getSelectedElement() == null || marketplaceList.getSelectedElement().getProjectTemplateDTO() == null) {
                myInfoScrollPane.setVisible(false);
                return;
            }
            ProjectTemplateDTO projectTemplateDTO = marketplaceList.getSelectedElement().getProjectTemplateDTO();
            nameLabel.setText(projectTemplateDTO.getName());
            orgLabel.setText(projectTemplateDTO.getOrganizationName());
            timeLabel.setText(projectTemplateDTO.getUpdateTime());
            creatorLabel.setText(projectTemplateDTO.getCreateName());
            descLabel.setText(projectTemplateDTO.getDescription());
            myInfoScrollPane.setVisible(true);
            infoPanel.setVisible(true);
        });
    }

    private void createUIComponents() {
        myScrollPane = new JBScrollPane();
        myInfoScrollPane = new JBScrollPane();
        myDescScrollPane = new JBScrollPane();
        myDescScrollPane.setBorder(BorderFactory.createEmptyBorder());
        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.setBorder(myScrollPane.getBorder());
    }

    @NotNull
    @Override
    public String getId() {
        return getClass().toString();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "模板导入";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        getList(new MarketplaceListRequest());
        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    private JBLoadingPanel getLoadingPanel() {
        return (JBLoadingPanel) loadingPanel;
    }

    @Override
    public void apply() throws ConfigurationException {
    }

    @Override
    public void dispose() {

    }

    private void startLoading() {
        myScrollPane.setVisible(false);
        myInfoScrollPane.setVisible(false);
        getLoadingPanel().setVisible(true);
        getLoadingPanel().startLoading();
    }

    private void stopLoading() {
        myScrollPane.setVisible(true);
        myInfoScrollPane.setVisible(true);
        infoPanel.setVisible(false);
        getLoadingPanel().setVisible(false);
        getLoadingPanel().stopLoading();
    }

    private void getList(MarketplaceListRequest request) {
        ThreadUtils.execute(() -> {
            long st = System.currentTimeMillis();
            startLoading();
            try {
                MarketplaceListResponse response = hubClient.execute(request);
                if (response.isSuccess() && response.getList() != null) {
                    marketplaceList.clearElement();
                    for (ProjectTemplateDTO projectTemplateDTO : response.getList()) {
                        ListElement listElement = new ListElement(null,
                                projectTemplateDTO.getId(), projectTemplateDTO.getName() + "（" + projectTemplateDTO.getOrganizationName() + "）");
                        listElement.setProjectTemplateDTO(projectTemplateDTO);
                        marketplaceList.addElement(listElement);
                    }
                }
            } finally {
                long sleepTime = 500 - (System.currentTimeMillis() - st);
                if (sleepTime > 0) {
                    ThreadUtils.sleep(sleepTime);
                }
                stopLoading();
            }
        });
    }
}
