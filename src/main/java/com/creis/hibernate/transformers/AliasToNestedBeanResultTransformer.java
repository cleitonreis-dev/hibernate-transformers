package com.creis.hibernate.transformers;

import org.hibernate.HibernateException;
import org.hibernate.property.access.internal.PropertyAccessStrategyFieldImpl;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.hibernate.transform.AliasedTupleSubsetResultTransformer;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;

public class AliasToNestedBeanResultTransformer extends AliasedTupleSubsetResultTransformer{

    private final Class<?> rootClass;
    private AliasToBeanResultTransformer rootTransformer;
    private boolean initialized;
    private List<Alias> rootAliasesWithIndexes;
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

        Object rootBean = transformRootBean(tuple);
        transformNested(rootBean, tuple);

        return rootBean;
    }

    private void transformNested(Object rootBean, Object[] tuple) {
        nestedAliases.forEach(nestedAlias->{
            Object subBean = nestedAlias.transform(tuple);
            setValue(rootBean, nestedAlias.rootAliasName, subBean);
        });
    }

    private Object transformRootBean(Object[] tuple) {
        Object[] rootTuple = new Object[rootAliasesWithIndexes.size()];
        String[] rootAliases = new String[rootAliasesWithIndexes.size()];

        rootAliasesWithIndexes.forEach(alias->{
            rootAliases[alias.index] = alias.name;
            rootTuple[alias.index] = tuple[alias.originalIndex];
        });

        return rootTransformer.transformTuple(rootTuple,rootAliases);
    }

    private void initialize(String[] aliases) {
        rootAliasesWithIndexes = new ArrayList<>();
        Map<String,NestedAliases> nestedAliasesMap = new HashMap<>();
        Map<String,Field> allRootFields = findAllFields(rootClass,new HashMap<>());

        for(int i = 0; i < aliases.length; i++){
            int nestedDelimiterIndex = aliases[i].indexOf('.');

            if(nestedDelimiterIndex < 0){
                rootAliasesWithIndexes.add(new Alias(aliases[i],rootAliasesWithIndexes.size(),i));
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
        final Class<?> nestedBeanClass;
        final List<Alias> aliases;
        final AliasToNestedBeanResultTransformer nestedTransformer;

        NestedAliases(String rootAliasName, Class<?> nestedBeanClass) {
            this.rootAliasName = rootAliasName;
            this.nestedBeanClass = nestedBeanClass;
            nestedTransformer = new AliasToNestedBeanResultTransformer(nestedBeanClass);
            aliases = new ArrayList<>();
        }

        Object transform(Object[] tuple){
            Object[] nestedTuple = new Object[aliases.size()];
            String[] newNestedAliases = new String[aliases.size()];

            aliases.forEach(alias->{
                newNestedAliases[alias.index] = alias.name;
                nestedTuple[alias.index] = tuple[alias.originalIndex];
            });

            return nestedTransformer.transformTuple(nestedTuple,newNestedAliases);
        }
    }
}
