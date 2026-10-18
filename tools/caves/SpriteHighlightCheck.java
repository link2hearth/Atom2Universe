import com.Atom2Universe.app.games.caves.world.SpriteHitMask;

public class SpriteHighlightCheck {
 public static void main(String[] args) {
  int[] pixels={0,0x80000000,0,0, 0xffffffff,0xffffffff,0,0x7fffffff, 0,0,0xffffffff,0};
  SpriteHitMask mask=new SpriteHitMask(4,3,pixels);
  for(float offset:new float[]{-2f,2f}) {
   float[] v=mask.highlight(offset,0,offset,.1f,.6f,1,0,0);
   double area=0;
   for(int i=0;i<v.length;i+=18) {
    double ax=v[i+6]-v[i],ay=v[i+7]-v[i+1],az=v[i+8]-v[i+2];
    double bx=v[i+12]-v[i],by=v[i+13]-v[i+1],bz=v[i+14]-v[i+2];
    double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
    area+=Math.sqrt(nx*nx+ny*ny+nz*nz)/2;
    if(nx*(-offset-.5)+nz*(-offset-.5)<=0)throw new AssertionError("Back-facing highlight");
    double cx=(v[i]+v[i+6]+v[i+12])/3.0-offset,cy=(v[i+1]+v[i+7]+v[i+13])/3.0,cz=(v[i+2]+v[i+8]+v[i+14])/3.0-offset;
    double along=Math.abs(nz)>0?cx:cz;
    int px=(int)((along-.1)/.8*4),py=(int)((1-cy/.6)*3);
    if((pixels[py*4+px]>>>24)<128)throw new AssertionError("Transparent area highlighted");
   }
   if(Math.abs(area-4.0/12*.8*.6*2)>1e-6)throw new AssertionError("Incorrect opaque coverage: "+area);
  }
  if(new SpriteHitMask(1,1,new int[]{0}).highlight(0,0,0,0,1,1,0,0).length!=0)throw new AssertionError("Empty mask");
  System.out.println("Sprite highlight: opaque area only, alpha threshold, both planes and camera sides verified.");
 }
}
