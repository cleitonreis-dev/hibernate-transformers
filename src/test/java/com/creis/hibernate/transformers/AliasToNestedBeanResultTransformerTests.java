package com.creis.hibernate.transformers;

import org.hibernate.HibernateException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AliasToNestedBeanResultTransformerTests {

    @Rule
    public ExpectedException exceptions = ExpectedException.none();

    @Test
    public void shouldTransformBasicBean(){
        Object[] tuple = {1,"test"};
        String[] aliases = {"id","description"};

        BasicBean bean = (BasicBean)AliasToNestedBeanResultTransformer.of(BasicBean.class)
                .transformTuple(tuple,aliases);

        assertEquals(tuple[0], bean.id);
        assertEquals(tuple[1], bean.description);
    }

    @Test
    public void shouldTransformNestedBean(){
        Object[] tuple = {1,"test",2,"test 2"};
        String[] aliases = {"id","description","nested.id","nested.description"};

        BeanWithNestedBasicOne bean = (BeanWithNestedBasicOne)
                AliasToNestedBeanResultTransformer.of(BeanWithNestedBasicOne.class)
                .transformTuple(tuple,aliases);

        assertEquals(tuple[0], bean.id);
        assertEquals(tuple[1], bean.description);
        assertNotNull(bean.nested);
        assertEquals(tuple[2], bean.nested.id);
        assertEquals(tuple[3], bean.nested.description);
    }

    @Test
    public void shouldTransformMoreThanTwoLevelsOfNestedBean(){
        Object[] tuple = {
                1,"test",
                2,"test 2",
                3,"test 3",
                4,"test 4"};
        String[] aliases = {
                "nested.id","nested.description",
                "nestedMultiLevel.id","nestedMultiLevel.description",
                "nestedMultiLevel.nested.id","nestedMultiLevel.nested.description"};

        MultiLevelNestedBean bean = (MultiLevelNestedBean)
                AliasToNestedBeanResultTransformer.of(MultiLevelNestedBean.class)
                        .transformTuple(tuple,aliases);

        assertNotNull(bean.nested);
        assertEquals(tuple[0], bean.nested.id);
        assertEquals(tuple[1], bean.nested.description);

        assertNotNull(bean.nestedMultiLevel);
        assertEquals(tuple[2], bean.nestedMultiLevel.id);
        assertEquals(tuple[3], bean.nestedMultiLevel.description);

        assertNotNull(bean.nestedMultiLevel.nested);
        assertEquals(tuple[4], bean.nestedMultiLevel.nested.id);
        assertEquals(tuple[5], bean.nestedMultiLevel.nested.description);
    }

    @Test
    public void shouldTransformInheritedFields(){
        Object[] tuple = {1,"test",new Date(),2,"test 2"};
        String[] aliases = {"id","description","createdAt","nested.id","nested.description"};

        InheritedBean bean = (InheritedBean)
                AliasToNestedBeanResultTransformer.of(InheritedBean.class)
                        .transformTuple(tuple,aliases);

        BeanWithNestedBasicOne parent = (BeanWithNestedBasicOne)bean;
        assertEquals(tuple[0], parent.id);
        assertEquals(tuple[1], parent.description);
        assertEquals(tuple[2], bean.createdAt);
        assertNotNull(parent.nested);
        assertEquals(tuple[3], parent.nested.id);
        assertEquals(tuple[4], parent.nested.description);
    }

    @Test
    public void shouldThrowExceptionWhenNestedFieldNotFound(){
        exceptions.expect(HibernateException.class);
        exceptions.expectMessage("Field nest not found in class " + BeanWithNestedBasicOne.class.getName());

        Object[] tuple = {1,"test",2,"test 2"};
        String[] aliases = {"id","description","nest.id","nest.description"};

        AliasToNestedBeanResultTransformer.of(BeanWithNestedBasicOne.class).transformTuple(tuple,aliases);
    }

    @Test
    public void shouldValidateRequiredParametersOfStaticFactoryMethodOf(){
        exceptions.expect(NullPointerException.class);
        exceptions.expectMessage("beanClass is required");
        AliasToNestedBeanResultTransformer.of(null);
    }


    public static class BasicBean{
        private Integer id;
        private String description;
    }

    public static class BeanWithNestedBasicOne{
        private Integer id;
        private String description;
        private BasicBean nested;
    }

    public static class MultiLevelNestedBean{
        private BasicBean nested;
        private BeanWithNestedBasicOne nestedMultiLevel;
    }

    public static class InheritedBean extends BeanWithNestedBasicOne{
        private Date createdAt;
    }
}