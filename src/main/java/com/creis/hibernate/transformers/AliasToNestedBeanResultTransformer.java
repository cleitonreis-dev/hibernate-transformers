package com.creis.hibernate.transformers;

import org.hibernate.HibernateException;
import org.hibernate.property.access.internal.PropertyAccessStrategyFieldImpl;
import org.hibernate.property.access.spi.Setter;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.hibernate.transform.AliasedTupleSubsetResultTransformer;
import org.hibernate.transform.ResultTransformer;

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
            nestedAlias.setter.set(rootBean, subBean, null);
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
        Map<String,Class<?>> allRootFieldsType = findAllFieldsType(rootClass,new HashMap<>());

        for(int i = 0; i < aliases.length; i++){
            int nestedDelimiterIndex = aliases[i].indexOf('.');

            if(nestedDelimiterIndex < 0){
                rootAliases.add(new Alias(aliases[i],rootAliases.size(),i));
                continue;
            }

            String alias = aliases[i].substring(0, nestedDelimiterIndex);
            String nestedAlias = aliases[i].substring(nestedDelimiterIndex + 1);

            if(!nestedAliasesMap.containsKey(alias)){
                nestedAliasesMap.put(alias, new NestedAliases(
                    alias, findFieldType(alias, allRootFieldsType), getSetter(alias)
                ));
            }

            List<Alias> nestedAliases = nestedAliasesMap.get(alias).aliases;
            nestedAliases.add(new Alias(nestedAlias,nestedAliases.size(),i));
        }

        nestedAliases = nestedAliasesMap.values();
        rootTransformer = new AliasToBeanResultTransformer(rootClass);
        initialized = true;
    }

    private Class<?> findFieldType(String fieldName, Map<String,Class<?>> allFieldsType){
        if(!allFieldsType.containsKey(fieldName)){
            throw new HibernateException(format("Field %s not found in class %s",fieldName,rootClass.getName()));
        }

        return allFieldsType.get(fieldName);
    }

    private Setter getSetter(String fieldName){
        return PropertyAccessStrategyFieldImpl.INSTANCE
                .buildPropertyAccess(rootClass, fieldName)
                .getSetter();
    }

    private static Map<String,Class<?>> findAllFieldsType(Class<?> targetClass, Map<String,Class<?>> fieldsMap){
        if(targetClass.getSuperclass() != Object.class){
            findAllFieldsType(targetClass.getSuperclass(), fieldsMap);
        }

        Stream.of(targetClass.getDeclaredFields())
                .forEach(field -> fieldsMap.put(field.getName(), field.getType()));

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
        final Setter setter;

        NestedAliases(String rootAliasName, Class<?> nestedBeanClass, Setter setter) {
            this.rootAliasName = rootAliasName;
            nestedTransformer = new AliasToNestedBeanResultTransformer(nestedBeanClass);
            aliases = new ArrayList<>();
            this.setter = setter;
        }
    }
}
