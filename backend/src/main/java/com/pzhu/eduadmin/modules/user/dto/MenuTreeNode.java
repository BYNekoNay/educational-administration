package com.pzhu.eduadmin.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前用户可见菜单树节点，用于前端侧栏动态渲染。
 * 按权限过滤 + 树形嵌套结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuTreeNode {

    private Long id;
    private Long parentId;
    private String menuName;
    private String icon;
    private String path;
    private String permissionCode;
    private Integer sortOrder;
    private Integer visible;
    private List<MenuTreeNode> children;

    public static MenuTreeNodeBuilder builder() {
        return new MenuTreeNodeBuilder();
    }

    public static class MenuTreeNodeBuilder {
        private List<MenuTreeNode> children;

        public MenuTreeNodeBuilder children(List<MenuTreeNode> children) {
            this.children = children;
            return this;
        }

        public MenuTreeNodeBuilder addChild(MenuTreeNode child) {
            if (this.children == null) {
                this.children = new ArrayList<>();
            }
            this.children.add(child);
            return this;
        }
    }
}
