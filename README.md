Simple project to implement some useful hibernate transformers
==============================================================
Tests made with Hibernate version 5.2.6.Final

Usage
-----
>[com.creis.hibernate.transformers.AliasToNestedBeanResultTransformer.java](https://github.com/cleitonreis-dev/hibernate-transformers/blob/master/src/main/java/com/creis/hibernate/transformers/AliasToNestedBeanResultTransformer.java)

Transforms multi-level nested beans with inheritance support

Example
-------
TO's
```java
public static class BasicTO{
    private Integer id;
    private String description;
}

public static class ComplexTO{
    private Integer id;
    private String description;
    private BasicTO basicTo;
}
```
Native SQL to keep it simple, but it will also work fine with projection list
```SQL
select ct.id as id, ct.description as description, bt.id as "basicTo.id", bt.description as "basicTo.description"
from complex_table ct
inner join basic_table bt on bt.id = ct.basic_table_id
```
List with transformer
```java
List<ComplexTO> complexTOs = (List<ComplexTO>)session
    .createNativeQuery(sql)
    .setResultTransformer(AliasToNestedBeanResultTransformer.of(ComplexTO.class))
    .list();
```
