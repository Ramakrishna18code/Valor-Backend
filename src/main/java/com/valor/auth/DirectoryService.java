package com.valor.auth;

import com.valor.workflow.WorkflowDtos.PageView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only scalar projections: no user entities or authentication fields are selected. */
@Service
@Transactional(readOnly=true)
class DirectoryService {
    private final EntityManager em;
    private final AssetIdentityAccess identities;
    DirectoryService(EntityManager em, AssetIdentityAccess identities) { this.em=em; this.identities=identities; }

    PageView<DirectoryController.Technician> technicians(int page,int size,String q,Boolean active) {
        return directory(TechnicianProfile.class,DirectoryController.Technician.class,page,size,q,active,true);
    }
    PageView<DirectoryController.Customer> customers(int page,int size,String q,Boolean active) {
        return directory(CustomerProfile.class,DirectoryController.Customer.class,page,size,q,active,false);
    }
    private <P,T> PageView<T> directory(Class<P> profile,Class<T> view,int page,int size,String q,Boolean active,boolean technician) {
        identities.requireAdmin();
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid page");
        String search=q==null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if(search.length()>254) throw new IllegalArgumentException("Invalid search");
        var cb=em.getCriteriaBuilder();
        var query=cb.createQuery(view); var root=query.from(profile); var user=root.join("user");
        Expression<Boolean> enabled=cb.and(cb.isTrue(user.get("active")),cb.isTrue(root.get("active")));
        if(technician) query.select(cb.construct(view,user.get("id"),user.get("email"),enabled,root.get("id"),
                root.get("employeeId"),root.get("assignedArea"),root.get("specialization"),root.get("availabilityStatus")));
        else query.select(cb.construct(view,user.get("id"),root.get("id"),root.get("fullName"),user.get("email"),
                user.get("phone"),enabled,root.get("status")));
        query.where(filters(cb,root,user,search,active,technician)).orderBy(cb.asc(root.get("id")));
        var items=em.createQuery(query).setFirstResult(page*size).setMaxResults(size).getResultList();
        var count=cb.createQuery(Long.class); var countRoot=count.from(profile); var countUser=countRoot.join("user");
        count.select(cb.count(countRoot)).where(filters(cb,countRoot,countUser,search,active,technician));
        long total=em.createQuery(count).getSingleResult();
        return new PageView<>(items,page,size,total,(int)Math.ceil((double)total/size));
    }
    private Predicate filters(CriteriaBuilder cb,Root<?> root,Join<?,?> user,String search,Boolean active,boolean technician) {
        var predicates=new ArrayList<Predicate>();
        predicates.add(cb.equal(user.get("role"),technician ? Role.TECHNICIAN : Role.CUSTOMER));
        if(active!=null) {
            Predicate enabled=cb.and(cb.isTrue(user.get("active")),cb.isTrue(root.get("active")));
            predicates.add(active ? enabled : cb.not(enabled));
        }
        if(!search.isEmpty()) {
            String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            var matches=new ArrayList<Predicate>();
            matches.add(cb.like(cb.lower(user.get("email")),pattern,'!'));
            if(technician) for(String field:List.of("employeeId","assignedArea","specialization"))
                matches.add(cb.like(cb.lower(root.get(field)),pattern,'!'));
            else {
                matches.add(cb.like(cb.lower(root.get("fullName")),pattern,'!'));
                matches.add(cb.like(cb.lower(user.get("phone")),pattern,'!'));
            }
            predicates.add(cb.or(matches.toArray(Predicate[]::new)));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }
}
