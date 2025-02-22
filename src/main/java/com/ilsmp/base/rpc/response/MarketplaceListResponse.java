package com.ilsmp.base.rpc.response;

import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.mars05.crud.hub.common.dto.ProjectTemplateDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 市场模板
 *
 * @author xioayu
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MarketplaceListResponse extends Response {
    private List<ProjectTemplateDTO> list;

    @Override
    public void setData(JSON data) {
        super.setData(data);
        if (data != null) {
            JSONArray list = ((JSONObject) data).getJSONArray("list");
            this.list = list.toJavaList(ProjectTemplateDTO.class);
        }
    }
}