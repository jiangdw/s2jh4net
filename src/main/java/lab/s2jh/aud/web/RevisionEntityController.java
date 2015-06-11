package lab.s2jh.aud.web;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;

import lab.s2jh.aud.service.RevisionEntityService;
import lab.s2jh.core.annotation.MenuData;
import lab.s2jh.core.annotation.MetaData;
import lab.s2jh.core.audit.envers.EntityRevision;
import lab.s2jh.core.audit.envers.ExtDefaultRevisionEntity;
import lab.s2jh.core.entity.PersistableEntity;
import lab.s2jh.core.exception.WebException;
import lab.s2jh.core.pagination.ExtPageRequest;
import lab.s2jh.core.security.AuthUserDetails;
import lab.s2jh.core.service.BaseService;
import lab.s2jh.core.util.EnumUtils;
import lab.s2jh.core.web.BaseController;
import lab.s2jh.core.web.json.JsonViews;
import lab.s2jh.module.auth.entity.User.AuthTypeEnum;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.hibernate.envers.Audited;
import org.hibernate.proxy.pojo.javassist.JavassistLazyInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Controller
@RequestMapping("/admin/aud/revision-entity")
public class RevisionEntityController extends BaseController<ExtDefaultRevisionEntity, Long> {

    private final static Logger logger = LoggerFactory.getLogger(RevisionEntityController.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private RevisionEntityService revisionEntityService;

    @Override
    protected BaseService<ExtDefaultRevisionEntity, Long> getEntityService() {
        return revisionEntityService;
    }

    @MenuData("配置管理:系统记录:业务操作记录")
    @RequiresPermissions("配置管理:系统记录:业务操作记录")
    @RequestMapping(value = "/user", method = RequestMethod.GET)
    protected String revisionEntityUserIndex(Model model) throws Exception {
        model.addAttribute("authTypeMap", EnumUtils.getEnumDataMap(AuthTypeEnum.class));
        return "admin/aud/revisionEntity-userIndex";
    }

    @RequiresPermissions("配置管理:系统记录:业务操作记录")
    @RequestMapping(value = "/user/list", method = RequestMethod.GET)
    @ResponseBody
    @JsonView(JsonViews.Admin.class)
    public Page<ExtDefaultRevisionEntity> findByPage(HttpServletRequest request) {
        return super.findByPage(ExtDefaultRevisionEntity.class, request);
    }

    /**
     * 版本数据主界面页面转向
     */
    @MenuData("配置管理:系统记录:数据变更记录")
    @RequiresPermissions("配置管理:系统记录:数据变更记录")
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    protected String revisionEntityDataIndex(Model model) throws Exception {
        Map<String, String> clazzMapping = Maps.newHashMap();

        //搜索所有entity对象，并自动进行自增初始化值设置
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<BeanDefinition> beanDefinitions = scan.findCandidateComponents("**.entity.**");
        for (BeanDefinition beanDefinition : beanDefinitions) {
            Class<?> entityClass = ClassUtils.getClass(beanDefinition.getBeanClassName());
            Audited audited = entityClass.getAnnotation(Audited.class);
            if (audited != null) {
                MetaData metaData = entityClass.getAnnotation(MetaData.class);
                if (metaData != null) {
                    clazzMapping.put(entityClass.getName(), metaData.value());
                } else {
                    clazzMapping.put(entityClass.getName(), entityClass.getName());
                }
            }
        }
        model.addAttribute("clazzMapping", clazzMapping);

        return "admin/aud/revision-index";
    }

    /**
     * 用于版本属性下拉列表集合
     * @return
     */
    public Map<Field, String> getRevisionFields(final Class<?> entityClass) {
        Map<Field, String> revisionFields = Maps.newLinkedHashMap();
        for (Field field : entityClass.getDeclaredFields()) {
            MetaData metaData = field.getAnnotation(MetaData.class);
            if (metaData != null && metaData.comparable()) {
                revisionFields.put(field, metaData != null ? metaData.value() : field.getName().toUpperCase());
            }
        }
        return revisionFields;
    }

    /**
     * Revision操作记录列表
     * 为了避免由于权限配置不严格，导致未授权的Controller数据操作访问，父类提供protected基础实现，子类根据需要覆写public然后调用基类方法
     * @return JSON集合数据
     * @throws ClassNotFoundException 
     */
    @MetaData(value = "版本数据列表")
    @RequiresPermissions("配置管理:系统记录:数据变更记录")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    protected Page<EntityRevision> revisionList(HttpServletRequest request) throws ClassNotFoundException {
        Class<?> clazz = ClassUtils.getClass(request.getParameter("clazz"));

        String property = request.getParameter("property");
        Boolean hasChanged = null;
        String changed = request.getParameter("changed");
        if (StringUtils.isNotBlank(changed)) {
            hasChanged = BooleanUtils.toBooleanObject(changed);
        }
        Long id = Long.valueOf(request.getParameter("id"));
        List<EntityRevision> entityRevisions = revisionEntityService.findEntityRevisions(clazz, id, property, hasChanged);
        for (EntityRevision entityRevision : entityRevisions) {
            Object entity = entityRevision.getEntity();
            ExtDefaultRevisionEntity revEntity = entityRevision.getRevisionEntity();
            //            if (entity instanceof OperationAuditable) {
            //                OperationAuditable aae = (OperationAuditable) entity;
            //                revEntity.setOldStateDisplay(aae.convertStateToDisplay(revEntity.getOldState()));
            //                revEntity.setNewStateDisplay(aae.convertStateToDisplay(revEntity.getNewState()));
            //                revEntity.setOperationEventDisplay(revEntity.getOperationEvent());
            //            } else {
            //                revEntity.setOldStateDisplay(revEntity.getOldState());
            //                revEntity.setNewStateDisplay(revEntity.getNewState());
            //                revEntity.setOperationEventDisplay(revEntity.getOperationEvent());
            //            }
        }

        return ExtPageRequest.buildPageResultFromList(entityRevisions);
    }

    /**
     * Revision版本数据对比显示
     */
    @MetaData(value = "版本数据对比")
    @RequiresPermissions("配置管理:系统记录:数据变更记录")
    @RequestMapping(value = "/compare", method = RequestMethod.GET)
    public String revisionCompare(HttpServletRequest request, @RequestParam("clazz") String clazz, @RequestParam("revs") Long[] revs)
            throws ClassNotFoundException {
        Class<?> entityClass = ClassUtils.getClass(request.getParameter("clazz"));

        List<EntityRevision> entityRevisions = revisionEntityService.findEntityRevisions(entityClass, revs);

        List<Map<String, String>> revEntityProperties = Lists.newArrayList();
        for (Map.Entry<Field, String> me : getRevisionFields(entityClass).entrySet()) {
            Field field = me.getKey();
            Map<String, String> revEntityProperty = Maps.newHashMap();
            revEntityProperty.put("name", me.getValue());
            //            if (revLeftEntity != null) {
            //                try {
            //                    Object value = FieldUtils.readDeclaredField(revLeftEntity.getEntity(), field.getName(), true);
            //                    String valueDisplay = convertPropertyDisplay(revLeftEntity.getEntity(), field, value);
            //                    revEntityProperty.put("revLeftPropertyValue", valueDisplay);
            //                } catch (IllegalAccessException e) {
            //                    throw new WebException(e.getMessage(), e);
            //                }
            //            }
            //            if (revRightEntity != null) {
            //                try {
            //                    Object value = FieldUtils.readDeclaredField(revRightEntity.getEntity(), field.getName(), true);
            //                    String valueDisplay = convertPropertyDisplay(revRightEntity.getEntity(), field, value);
            //                    revEntityProperty.put("revRightPropertyValue", valueDisplay);
            //                } catch (IllegalAccessException e) {
            //                    throw new WebException(e.getMessage(), e);
            //                }
            //
            //            }
            revEntityProperties.add(revEntityProperty);
        }
        //  request.setAttribute("revLeftEntity", revLeftEntity);
        // request.setAttribute("revRightEntity", revRightEntity);
        request.setAttribute("revEntityProperties", revEntityProperties);
        return "admin/aud/revisionEntity-compare";
    }

    /**
     * 将对象Value对象转换为显示字符串，子类可根据需要覆写此方法输出定制格式字符串
     * @param entity 版本数据实体对象
     * @param field 版本字段属性
     * @param value 版本属性数据值
     * @return 格式化后处理的字符串
     */
    protected String convertPropertyDisplay(Object entity, Field field, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof PersistableEntity) {
            @SuppressWarnings("rawtypes")
            PersistableEntity persistableEntity = (PersistableEntity) value;
            String label = "N/A";
            try {
                label = persistableEntity.getDisplay();
            } catch (EntityNotFoundException e) {
                //Hibernate Envers默认始终查询对应Audit版本数据，有可能关联对象之前没有Audit记录，从而会导致Envers抛出未找到数据异常
                //此处做Hack处理：如果没有找到关联Audit记录，则查询关联主对象记录
                try {
                    //从Hibernate AOP增强对象反查对应实体对象数据
                    JavassistLazyInitializer jli = (JavassistLazyInitializer) FieldUtils.readDeclaredField(value, "handler", true);
                    Class entityClass = jli.getPersistentClass();
                    Serializable id = jli.getIdentifier();
                    Object obj = entityManager.find(entityClass, id);
                    PersistableEntity auditTargetEntity = (PersistableEntity) obj;
                    label = auditTargetEntity.getDisplay();
                } catch (IllegalAccessException iae) {
                    logger.warn(e.getMessage());
                }
            }
            return label;
        }
        return String.valueOf(value);
    }

}