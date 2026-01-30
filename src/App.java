import java.util.Scanner;
public class App {
    public static void main(String[] args) {
         System.out.println("TinhTong");
        Scanner sc = new Scanner(System.in);
		System.out.print("Nhap so a: ");
		int a = sc.nextInt();
		System.out.print("Nhap so b: ");
		int b = sc.nextInt();
        if (a<= 0 ||b<=0 ) {
            System.out.println("Vui long nhap so nguyen duong");
        }      
		System.out.println(a+b);
        System.out.println(a-b);
        System.out.println(a/b);
        System.out.println(a*b);
        System.out.println(a%b);
		sc.nextLine();
		System.out.println("So sanh");
        if (a>b) {
            System.out.println("so a lon hon so b");
        }else if (a<b) {
            System.out.println("so a nho hon so b");
        }else {
            System.out.println("hai so bang nhau");
        }
        sc.close();
    }
}