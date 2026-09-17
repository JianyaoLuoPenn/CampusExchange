package com.zosh.campus;
import com.zosh.model.User;
import com.zosh.domain.USER_ROLE;
import com.zosh.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.List;
@Component @ConditionalOnProperty(name="campus.demo",havingValue="true") @RequiredArgsConstructor
public class DemoData implements CommandLineRunner {
    private final UserRepository users; private final PasswordEncoder passwords; private final MarketplaceService market;
    public void run(String... args) {
        // Opt-in fictional accounts only. No original default administrator is created.
        if(users.findByEmail("maya@example.test")!=null)return;
        account("Maya Chen","maya@example.test");account("Alex Rivera","alex@example.test");account("Jordan Lee","jordan@example.test");
        String[] titles={"Oak study desk","Noise-cancelling headphones","Calculus textbook, 9th edition","Kitchen starter set","Reading chair","27-inch monitor"};
        String[] cats={"Furniture","Electronics","Textbooks","Home essentials","Furniture","Electronics"};
        long[] prices={6500,4500,2000,1800,4000,8000};
        for(int i=0;i<titles.length;i++) market.publish(i%2==0?"maya@example.test":"jordan@example.test",new CampusDtos.ListingInput(titles[i],"Fictional demo listing. Clean, well cared for and ready for a new home. Meet in the building lobby after reserving.",cats[i],i%3==0?"Like new":"Good",i<4?"North Campus":"South Campus",i%2==0?"Maple Court":"River House","Building lobby","DEMO ONLY — "+(100+i)+" Example Lane, lobby desk",prices[i],i%2==0?1000:0,List.of(Instant.now().plus(Duration.ofDays(2)),Instant.now().plus(Duration.ofDays(3))),List.of()));
    }
    private void account(String name,String email) {User u=new User();u.setFullName(name);u.setEmail(email);u.setPassword(passwords.encode("CampusDemo123!"));u.setRole(USER_ROLE.ROLE_CUSTOMER);users.save(u);}
}
