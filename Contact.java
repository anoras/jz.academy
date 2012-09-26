public class Contact {
    public long id;
    public String firstName;
    public String lastName;
    public String email;
    public static final Contact[] testData = new Contact[] {
            new Contact(){{
                id=1;
                firstName="Barry";
                lastName="White";
            }},
            new Contact(){{
                id=2;
                firstName="Marvin";
                lastName="Gaye";
            }}
    };
}