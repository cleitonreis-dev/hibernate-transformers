package com.creis.hibernate.transformers;

import org.hibernate.HibernateException;
import org.hibernate.property.access.internal.PropertyAccessStrategyFieldImpl;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.hibernate.transform.AliasedTupleSubsetResultTransformer;
import org.hibernate.transform.ResultTransformer;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;

public class AliasToNestedBeanResultTransformer extends AliasedTupleSubsetResultTransformer{

    private final Class<?> rootClass;
    private AliasToBeanResultTransformer rootTransformer;
    private boolean initialized;
    private List<Alias> rootAliases;
    private Collection<NestedAliases> nestedAliases;

    public AliasToNestedBeanResultTransformer(Class<?> beanClass) {
        this.rootClass = beanClass;
    }

    public boolean isTransformedValueATupleElement(String[] strings, int i) {return false;}

    @Override
    public Object transformTuple(Object[] tuple, String[] aliases) {
        if(!initialized){
            initialize(aliases);
        }

        Object rootBean = transform(rootAliases, tuple, rootTransformer);
        transformNested(rootBean, tuple);

        return rootBean;
    }

    private void transformNested(Object rootBean, Object[] tuple) {
        nestedAliases.forEach(nestedAlias->{
            Object subBean =  transform(nestedAlias.aliases, tuple, nestedAlias.nestedTransformer);
            setValue(rootBean, nestedAlias.rootAliasName, subBean);
        });
    }

    private Object transform(Collection<Alias> aliases, Object[] tuple, ResultTransformer transformer) {
        Object[] newTuple = new Object[aliases.size()];
        String[] newAliases = new String[aliases.size()];

        aliases.forEach(alias->{
            newAliases[alias.index] = alias.name;
            newTuple[alias.index] = tuple[alias.originalIndex];
        });

        return transformer.transformTuple(newTuple,newAliases);
    }

    private void initialize(String[] aliases) {
        rootAliases = new ArrayList<>();
        Map<String,NestedAliases> nestedAliasesMap = new HashMap<>();
        Map<String,Field> allRootFields = findAllFields(rootClass,new HashMap<>());

        for(int i = 0; i < aliases.length; i++){
            int nestedDelimiterIndex = aliases[i].indexOf('.');

            if(nestedDelimiterIndex < 0){
                rootAliases.add(new Alias(aliases[i],rootAliases.size(),i));
                continue;
            }

            String alias = aliases[i].substring(0, nestedDelimiterIndex);
            String nestedAlias = aliases[i].substring(nestedDelimiterIndex + 1);

            if(!nestedAliasesMap.containsKey(alias)){
                nestedAliasesMap.put(alias, new NestedAliases(alias, findFieldType(alias, allRootFields)));
            }

            List<Alias> nestedAliases = nestedAliasesMap.get(alias).aliases;
            nestedAliases.add(new Alias(nestedAlias,nestedAliases.size(),i));
        }

        nestedAliases = nestedAliasesMap.values();
        rootTransformer = new AliasToBeanResultTransformer(rootClass);
        initialized = true;
    }

    private Class<?> findFieldType(String fieldName, Map<String,Field> allFields){
        if(!allFields.containsKey(fieldName)){
            throw new HibernateException(format("Field %s not found in class %s",fieldName,rootClass.getName()));
        }

        return allFields.get(fieldName).getType();
    }

    private void setValue(Object rootBean, String rootAliasName, Object subBean) {
        PropertyAccessStrategyFieldImpl.INSTANCE
                .buildPropertyAccess(rootClass, rootAliasName)
                .getSetter().set(rootBean, subBean, null);
    }

    private static Map<String,Field> findAllFields(Class<?> targetClass, Map<String,Field> fieldsMap){
        if(targetClass.getSuperclass() != Object.class){
            findAllFields(targetClass.getSuperclass(), fieldsMap);
        }

        Stream.of(targetClass.getDeclaredFields())
                .forEach(field -> fieldsMap.put(field.getName(), field));

        return fieldsMap;
    }

    public static AliasToNestedBeanResultTransformer instance(Class<?> beanClass){
        return new AliasToNestedBeanResultTransformer(beanClass);
    }

    private class Alias{
        final String name;
        final int index;
        final int originalIndex;

        Alias(String name, int index, int originalIndex) {
            this.name = name;
            this.index = index;
            this.originalIndex = originalIndex;
        }
    }

    private class NestedAliases {
        final String rootAliasName;
        final List<Alias> aliases;
        final AliasToNestedBeanResultTransformer nestedTransformer;

        NestedAliases(String rootAliasName, Class<?> nestedBeanClass) {
            this.rootAliasName = rootAliasName;
            nestedTransformer = new AliasToNestedBeanResultTransformer(nestedBeanClass);
            aliases = new ArrayList<>();
        }
    }
}
