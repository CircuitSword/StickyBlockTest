package Util;

import Factories.HyperScheduler;
import PositionalKeys.ChunkCoord;
import PositionalKeys.HyperKeys;
import PositionalKeys.LocalCoord;
import Settings.WorldRules;
import Storage.ChunkValues;
import Storage.YGenTracker;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.jctools.maps.NonBlockingHashMap;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class DataUtil {

    public static class YLocComparator implements Comparator<Location> {
        @Override
        public int compare(Location loc1, Location loc2) {
            return Double.compare(loc1.getY(), loc2.getY());
        }
    }

    public static class YCoordComparator implements Comparator<LocalCoord>{
        @Override
        public int compare(LocalCoord l1, LocalCoord l2){
            return Integer.compare(l1.parsedCoord&0xffff,l2.parsedCoord&0xffff);
        }
    }

    public static class YBlockComparator implements Comparator<Block>{
        @Override
        public int compare(Block b1, Block b2){
            return Integer.compare(b1.getY(),b2.getY());
        }
    }

    public static class YBlockTerraSort implements Comparator<Block>{
        private final NonBlockingHashMap<ChunkCoord,ChunkValues> chunkData;
        private int xl = 1000000;
        private int zl = 1000000;
        private float[] ySlope = null;
        public YBlockTerraSort(NonBlockingHashMap<ChunkCoord,ChunkValues> chunkData){
            this.chunkData=chunkData;
        }
        @Override
        public int compare(Block b1, Block b2){
            int x=b1.getX(),x4=x>>4,z=b1.getZ(),z4=z>>4,y1;
            x=x<<28>>28;z=z<<28>>28;
            if (xl != x4 || zl != z4) {
                xl = x4;
                zl = z4;
                ySlope = chunkData.get(Coords.CHUNK(x4, z4)).ySlopeTracker;
            }
            y1=b1.getY()-((int)(ySlope[0]+(ySlope[1]*x)+(ySlope[2]*z)+(ySlope[3]*(x*=x))+(ySlope[4]*(z*=z))+(ySlope[5]*(x*x))+(ySlope[6]*(z*z))));
            x=b2.getX();x4=x>>4;z=b2.getZ();z4=z>>4;
            x=x<<28>>28;z=z<<28>>28;
            if (xl != x4 || zl != z4) {
                xl = x4;
                zl = z4;
                ySlope = chunkData.get(Coords.CHUNK(x4, z4)).ySlopeTracker;
            }
            return Integer.compare(b2.getY()-((int)(ySlope[0]+(ySlope[1]*x)+(ySlope[2]*z)+(ySlope[3]*(x*=x))+(ySlope[4]*(z*=z))+(ySlope[5]*(x*x))+(ySlope[6]*(z*z)))),y1);
        }
    }

    public static class SortXYZlist implements Comparator<int[]>{
        @Override
        public int compare(int[] l1, int[] l2){
            return Integer.compare((l1[1]<<24)|(l1[0]<<20>>>20<<12)|(l1[2]<<20>>>20),(l2[1]<<24)|(l2[0]<<20>>>20<<12)|(l2[2]<<20>>>20));
        }
    }
    public static class SortBlockList implements Comparator<Block>{
        @Override
        public int compare(Block b1, Block b2){

            return Integer.compare((b1.getY()<<24)|(b1.getX()<<20>>>20<<12)|(b1.getZ()<<20>>>20),(b2.getY()<<24)|(b2.getX()<<20>>>20<<12)|(b2.getZ()<<20>>>20));
        }
    }

    public final static byte[] stampTime(byte[] bD, int date){
        bD[12]=(byte)(date);
        bD[11]=(byte)(date>>=4);
        bD[10]=(byte)(date>>=4);
        bD[9]=(byte)(date>>=4);
        bD[8]=(byte)(date>>=4);
        bD[7]=(byte)(date>>=4);
        bD[6]=(byte)(date>>4);
        return bD;
    }

    public final static boolean olderThan(byte[] ts, int date ){
        return((((((((((((ts[6]<<4)|ts[7])<<4)|ts[8])<<4)|ts[9])<<4)|ts[10])<<4)|ts[11])<<4)|ts[12])<date;
    }
    public final static boolean youngerThan(byte[] ts, int date){
        return((((((((((((ts[6]<<4)|ts[7])<<4)|ts[8])<<4)|ts[9])<<4)|ts[10])<<4)|ts[11])<<4)|ts[12])>date;
    }

    public final static boolean olderThanAndStamp(byte[] ts, int date, int current ){
        boolean olderThan=((((((((((((ts[6]<<4)|ts[7])<<4)|ts[8])<<4)|ts[9])<<4)|ts[10])<<4)|ts[11])<<4)|ts[12])<date;
        ts[12]=(byte)(current);
        ts[11]=(byte)(current>>=4);
        ts[10]=(byte)(current>>=4);
        ts[9]=(byte)(current>>=4);
        ts[8]=(byte)(current>>=4);
        ts[7]=(byte)(current>>=4);
        ts[6]=(byte)(current>>4);
        return olderThan;
    }

    public final static byte lowestTensile(byte[] i) {
        if(i[4]==0||i[5]==0)return 0;
        byte min=126;
        if(i[0]<min)min=i[0];
        if(i[1]<min)min=i[1];
        if(i[2]<min)min=i[2];
        if(i[3]<min)min=i[3];
        ++min;
        if(i[4]<min)min=i[4];
        if(i[5]<min)min=i[5];
        return min;
    }

    public final static int regionLoad(byte[] yregion){
        return yregion[0]+yregion[1]+yregion[2]+yregion[3]
            +yregion[4]+yregion[5]+yregion[6]+yregion[6]
            +yregion[8]+yregion[9]+yregion[10]+yregion[11]
            +yregion[12]+yregion[13]+yregion[14]+yregion[15];
    }

    public final static void populateBlockData(ChunkValues cv, byte[] yNoise){
        HashMap<LocalCoord,byte[]> blockData = cv.blockVals;
        int X,Xshift,XZshift,Z,currentHeight,Ymin;
        for (X = -1; ++X < 16;) {
            Xshift=X<<4;
            for (Z = -1; ++Z < 16;){
                XZshift=Xshift|Z;
                currentHeight = yNoise[XZshift];
                Ymin = currentHeight - 10;
                blockData.put(HyperKeys.localCoord[(currentHeight<<8)|XZshift],new byte[]{126,126,0,0,0,0,0,0,0,0,0,0,0});
                blockData.put(HyperKeys.localCoord[(--currentHeight<<8)|XZshift],new byte[]{0,0,1,1,2,0,0,0,0,0,0,0,0});
                while(--currentHeight>Ymin) {
                    blockData.put(HyperKeys.localCoord[(currentHeight<<8)|XZshift],new byte[]{0,0,1,1,1,0,0,0,0,0,0,0,0});
                }
                ++currentHeight;
                Ymin-=5;
                while(--currentHeight>Ymin){
                    blockData.put(HyperKeys.localCoord[(currentHeight<<8)|XZshift],new byte[]{0,0,127,0,0,0,0,0,0,0,0,0,0});
                }
            }
        }
    }

    public final static void loadChunkRegression(int xz, byte[] y_slope, YGenTracker ytrack){
        if(ytrack.partition_lvl!=1){
            int pos = (xz >>> 5 << 1) | ((xz >>> 2) & 1);
            if(ytrack.x48_count[pos]!=4){
                int i = (xz >>> 4 << 2) | ((xz >>> 1) & 4);
                byte[][] yt = ytrack.init_holder[i];
                if(yt==null)yt=new byte[4][];
                yt[((xz>>>3)&1)|(xz&1)]=y_slope;
                if(yt[0]!=null&&yt[1]!=null&&yt[2]!=null&&yt[3]!=null){
                    double[][] ymatr = new double[1][1024];
                    double[] yset = ymatr[0];
                    int xzr,xzshift,xl,xlshft,xzxl,zl;
                    byte[] yloc;
                    for(xzr=-1; ++xzr<4;){
                        yloc=yt[xzr];
                        xzshift=((xzr&1)<<4)|(xzr>>>1<<9);
                        for(xl=-1;++xl<16;){
                            xzxl=xzshift|(xl<<5);
                            xlshft=xl<<4;
                            for(zl=-1;++zl<16;){
                                yset[xzxl|zl]=yloc[xlshft|zl];
                            }
                        }
                    }
                    ytrack.init_holder[i]=null;
                    BlockRealMatrix yVals = new BlockRealMatrix(1,1024,ymatr,false);
                    yset=x2_matrix.solve(yVals).getColumn(0);
                    ytrack.y_est_x2[i]=new float[]{(float) yset[0],
                        (float) yset[1],(float) yset[2],
                        (float) yset[3],(float) yset[4],
                        (float) yset[5],(float) yset[6],
                        (float) yset[7],(float) yset[8]};
                    if(++ytrack.x48_count[pos]==4){
                        last_use_4x=WorldRules.G_TIME;
                        if(x4_matrix==null)initx4();
                        float[] y_est; ymatr= new double[1][4096]; yset=ymatr[0]; int float_pos =(xz>>>5<<3)|(((xz>>>2)&1)<<1),xhold,zhold;
                        double regx;
                        for(xzr=-1; ++xzr<4;){
                            y_est=ytrack.y_est_x2[float_pos|(xzr>>1<<2)|(xzr&1)];
                            xzshift=((xzr&1)<<4)|(xzr>>>1<<9);
                            for(xl=-1;++xl<16;){
                                xzxl=xzshift|(xl<<5);
                                xhold=xl;
                                regx=y_est[0]+y_est[1]*xhold+y_est[3]*(xhold*=xl)+y_est[5]*(xhold*=xl)+y_est[7]*(xhold*xl);
                                for(zl=-1;++zl<16;){
                                    zhold=zl;
                                    yset[xzxl|zl]=regx+y_est[2]*zhold+y_est[4]*(zhold*=zl)+y_est[6]*(zhold*=zl)+y_est[8]*(zhold*zl);
                                }
                            }
                        }
                        yVals = new BlockRealMatrix(1,4096,ymatr,false);
                        yset=x4_matrix.solve(yVals).getColumn(0);
                        y_est=new float[]{(float) yset[0],
                            (float) yset[1],(float) yset[2],
                            (float) yset[3],(float) yset[4],
                            (float) yset[5],(float) yset[6],
                            (float) yset[7],(float) yset[8],
                            (float) yset[9],(float) yset[10],
                            (float) yset[11],(float) yset[12],
                            (float) yset[13],(float) yset[14],
                            (float) yset[15],(float) yset[16]};
                        for(xzr=-1; ++xzr<4;) {
                            ytrack.y_est_x2[float_pos | (xzr >> 1 << 2) | (xzr & 1)]=y_est;
                        }
                        if(++ytrack.x48_count[5]==4){
                            last_use_8x=WorldRules.G_TIME;
                            if(x8_matrix==null)initx8();
                            ymatr= new double[1][16384]; yset=ymatr[0];
                            for(xzr=-1; ++xzr<4;){
                                y_est=ytrack.y_est_x2[xzr<<2];
                                xzshift=((xzr&1)<<5)|(xzr>>>1<<11);
                                for(xl=-1;++xl<32;){
                                    xzxl=xzshift|(xl<<6);
                                    xhold=xl;
                                    regx=y_est[0]+y_est[1]*xhold+y_est[3]*(xhold*=xl)+y_est[5]*(xhold*=xl)+y_est[7]*(xhold*=xl)
                                        +y_est[9]*(xhold*=xl)+y_est[11]*(xhold*=xl)+y_est[13]*(xhold*=xl)+y_est[15]*(xhold*xl);
                                    for(zl=-1;++zl<32;){
                                        zhold=zl;
                                        yset[xzxl|zl]=regx+y_est[2]*zhold+y_est[4]*(zhold*=zl)+y_est[6]*(zhold*=zl)+y_est[8]*(zhold*=zl)
                                            +y_est[10]*(zhold*=zl)+y_est[12]*(zhold*=zl)+y_est[14]*(zhold*=zl)+y_est[16]*(zhold*zl);
                                    }
                                }
                            }
                            yVals = new BlockRealMatrix(1,16384,ymatr,false);
                            yset=x8_matrix.solve(yVals).getColumn(0);
                            ytrack.y_est_final=new float[]{(float) yset[0],
                                (float) yset[1],(float) yset[2], (float) yset[3],(float) yset[4],
                                (float) yset[5],(float) yset[6], (float) yset[7],(float) yset[8],
                                (float) yset[9],(float) yset[10], (float) yset[11],(float) yset[12],
                                (float) yset[13],(float) yset[14], (float) yset[15],(float) yset[16],
                                (float) yset[17],(float) yset[18], (float) yset[19],(float) yset[20],
                                (float) yset[21],(float) yset[22], (float) yset[23],(float) yset[24],
                                (float) yset[25],(float) yset[26], (float) yset[27],(float) yset[28],
                                (float) yset[29],(float) yset[30], (float) yset[31],(float) yset[32]};
                            ytrack.init_holder=null;
                            ytrack.y_est_x2=null;
                            ytrack.x48_count=null;
                            ytrack.partition_lvl=1;
                        }
                    }
                }
            }
        }
    }

    public final static ChunkValues initYNoiseMarker(ChunkValues cv){
        HashMap<LocalCoord,byte[]> blockVals = cv.blockVals;
        int z4,xl,zl, ypos,xshift,xzshift,xxz,ytotal;
        for(int x4=4;--x4>=0;){
            xshift=x4<<6;
            for(z4=4;--z4>=0;){
                xzshift=xshift|(z4<<2);
                ytotal=0;
                for(xl=4;--xl>=0;){
                    xxz=xzshift|(xl<<4);
                    for(zl=4;--zl>=0;){
                        for(ypos=-1;blockVals.get(HyperKeys.localCoord[((++ypos)<<8)|(xxz|zl)])==null;);
                        ytotal+=ypos;
                    }
                }
                cv.ySlopeTracker[(x4<<2)|z4]=(byte)(ytotal>>>4);
            }
        }
        return cv;
    }

    public final static float[] initYregressionVals(ChunkValues cv, byte[] yNoise, int degree){
        final WeightedObservedPoints points = new WeightedObservedPoints();
        for(int xz=-1;++xz<256;){
            points.add(xz,yNoise[xz]);
        }

        final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree);
        double[] betad = fitter.fit(points.toList());
        float[] betaf = new float[betad.length+1];
        betaf[0]=degree;
        for(int i = betad.length;--i>-1;){
            betaf[i+1]=(float)betad[i];
        }
        return betaf;
    }

    private static DecompositionSolver x2_matrix; static{
        double[] matrixCol;
        double[][] matrixArray = new double[256][9];
        int x,z,j,xshift;
        for(x=16;--x>-1;){
            xshift=x<<4;
            for(z=16;--z>-1;){
                matrixCol=matrixArray[xshift|z];
                for(j=10;(j-=2)>0;){
                    matrixCol[j]=Math.pow((z<<1),(j>>1));
                    matrixCol[j-1]=Math.pow((x<<1),(j>>1));
                }
                matrixCol[0]=1;
            }
        }
        x2_matrix=new QRDecomposition(new BlockRealMatrix(matrixArray)).getSolver();
    }
    private static DecompositionSolver x4_matrix=null;
    private static int last_use_4x;
    private final static void initx4(){
        double[] matrixCol;
        double[][] matrixArray = new double[1024][17];
        int x,z,j,xshift;
        for(x=32;--x>-1;){
            xshift=x<<5;
            for(z=32;--z>-1;){
                matrixCol=matrixArray[xshift|z];
                for(j=18;(j-=2)>0;){
                    matrixCol[j]=Math.pow((z<<1),(j>>1));
                    matrixCol[j-1]=Math.pow((x<<1),(j>>1));
                }
                matrixCol[0]=1;
            }
        }
        x4_matrix=new QRDecomposition(new BlockRealMatrix(matrixArray)).getSolver();
        HyperScheduler.scheduledExecutor.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(last_use_4x<=WorldRules.G_TIME-6) {
                    x4_matrix = null;
                    cancel();
                }
            }
        },30,30, TimeUnit.SECONDS);
    }
    private static DecompositionSolver x8_matrix=null;
    private static int last_use_8x;
    private final static void initx8(){
        double[] matrixCol;
        double[][] matrixArray = new double[4096][33];
        int x,z,j,xshift;
        for(x=64;--x>-1;){
            xshift=x<<6;
            for(z=64;--z>-1;){
                matrixCol=matrixArray[xshift|z];
                for(j=34;(j-=2)>0;){
                    matrixCol[j]=Math.pow((z<<1),(j>>1));
                    matrixCol[j-1]=Math.pow((x<<1),(j>>1));
                }
                matrixCol[0]=1;
            }
        }
        x8_matrix=new QRDecomposition(new BlockRealMatrix(matrixArray)).getSolver();
        HyperScheduler.scheduledExecutor.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(last_use_8x<=WorldRules.G_TIME-6) {
                    x8_matrix = null;
                    cancel();
                }
            }
        },30,30, TimeUnit.SECONDS);
    }

    /*public final static float[] polyYregression(byte[] yNoise, boolean maxBlockRes, boolean isCubic){
        int blockres = maxBlockRes?256:64,poly_degree=isCubic?7:5;
        *//*double[][] matrix_entry = new double[blockres][],matrix_template=maxBlockRes?isCubic?cubicMaxRes:quadraticMaxRes:isCubic?cubicLowRes:quadraticLowRes;
        System.arraycopy(matrix_template,0,matrix_entry,0,blockres);
        for(int i=blockres;--i>-1;){
            System.arraycopy(matrix_entry[i],0,matrix_template[i],0,poly_degree);
        }*//*
        BlockRealMatrix yVals = new BlockRealMatrix(blockres,1);
        for(int y=blockres;--y>-1;){
            yVals.setEntry(y,0,yNoise[y]);
        }
        RealMatrix betas = cubicMaxRes.solve(yVals);
        double[] betasr = betas.getColumn(0);
        Bukkit.broadcastMessage(betasr[0]+", "+(float) betasr[1]+", "+(float) betasr[2]+", "+(float) betasr[3]+", "+(float) betasr[4]+", "+(float) betasr[5]+", "+(float) betasr[6]);
        return new float[]{(float) betasr[0],(float) betasr[1],(float) betasr[2],(float) betasr[3],(float) betasr[4],(float) betasr[5],(float) betasr[6]};
    }*/

    public static class XORGenRandom extends Random {
        private long seed;
        private int shift;

        public XORGenRandom(int shift){
            seed=System.currentTimeMillis();
            this.shift=64-shift;
        }

        @Override
        public void setSeed(long seed) {
            this.seed = seed==0?1:seed;
        }

        @Override
        public final long nextLong() {
            seed ^= seed << 21;
            seed ^= seed >>> 35;
            return seed ^= seed << 4;
        }

        public final long nextLongShift() {
            seed ^= seed << 21;
            seed ^= seed >>> 35;
            return (seed ^= seed << 4)>>>shift;
        }
    }

    public static int TIME_SEGMENT(Long currentMillis, int timeBlockSeconds){
        return (int) Math.floor(currentMillis/(timeBlockSeconds*1000));
    }

}
