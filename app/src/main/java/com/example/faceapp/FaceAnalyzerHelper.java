package com.example.faceapp;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FaceAnalyzerHelper {
    public interface Callback { void onReport(AnalysisReport report); void onError(Exception error); }

    private final FaceDetector detector;

    public FaceAnalyzerHelper() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        detector = FaceDetection.getClient(options);
    }

    public void analyze(Bitmap source, Callback callback) {
        com.google.mlkit.vision.common.InputImage image = com.google.mlkit.vision.common.InputImage.fromBitmap(source, 0);
        Task<List<Face>> task = detector.process(image);
        task.addOnSuccessListener(faces -> {
            if (faces.isEmpty()) { callback.onReport(createFallbackReport()); return; }
            callback.onReport(analyzeFace(source, faces.get(0)));
        }).addOnFailureListener(callback::onError);
    }

    private AnalysisReport analyzeFace(Bitmap bmp, Face face) {
        Rect box = face.getBoundingBox();
        List<PointF> forehead = points(face, FaceContour.FACE);
        List<PointF> leftCheek = points(face, FaceContour.LEFT_CHEEK);
        List<PointF> rightCheek = points(face, FaceContour.RIGHT_CHEEK);
        List<PointF> leftBrow = union(points(face, FaceContour.LEFT_EYEBROW_TOP), points(face, FaceContour.LEFT_EYEBROW_BOTTOM));
        List<PointF> rightBrow = union(points(face, FaceContour.RIGHT_EYEBROW_TOP), points(face, FaceContour.RIGHT_EYEBROW_BOTTOM));
        List<PointF> upperLip = points(face, FaceContour.UPPER_LIP_TOP);
        List<PointF> lowerLip = points(face, FaceContour.LOWER_LIP_BOTTOM);

        List<Rect> skinRegions = new ArrayList<>();
        Rect left = regionFromPoints(leftCheek, 0.18f);
        Rect right = regionFromPoints(rightCheek, 0.18f);
        Rect foreheadRect = new Rect(box.left + box.width()/5, box.top + box.height()/8,
                box.right - box.width()/5, box.top + box.height()/3);
        if (left.width() > 0) skinRegions.add(left);
        if (right.width() > 0) skinRegions.add(right);
        if (foreheadRect.width() > 0) skinRegions.add(foreheadRect);

        double brightness = 0, uniformity = 0, microTexture = 0, baseline = 0, variance = 0;
        int count = 0;
        for (Rect r : skinRegions) {
            for (int y = r.top; y < r.bottom; y += 3) {
                for (int x = r.left; x < r.right; x += 3) {
                    if (x >= 0 && y >= 0 && x < bmp.getWidth() && y < bmp.getHeight()) {
                        int p = bmp.getPixel(x, y); brightness += luminance(p); count++;
                    }
                }
            }
        }
        baseline = count == 0 ? 160 : brightness / count;

        for (Rect r : skinRegions) {
            List<Double> vals = new ArrayList<>();
            for (int y = r.top; y < r.bottom; y += 4) for (int x = r.left; x < r.right; x += 4) {
                if (x >= 0 && y >= 0 && x < bmp.getWidth() && y < bmp.getHeight()) vals.add(luminance(bmp.getPixel(x,y)));
            }
            if (!vals.isEmpty()) {
                double mean = average(vals), localVariance = 0;
                for (double v: vals) localVariance += (v - mean)*(v - mean);
                localVariance /= vals.size();
                uniformity += Math.sqrt(localVariance); microTexture += localContrast(bmp, r);
            }
        }
        variance = skinRegions.isEmpty() ? 0 : uniformity / skinRegions.size();
        double texture = skinRegions.isEmpty() ? 0 : microTexture / skinRegions.size();

        int glow = clamp((int)Math.round((brightness/count/255.0)*55 + (100-Math.min(100, variance))*0.45), 0, 100);
        int moisture = clamp((int)Math.round(100-Math.min(100, variance*1.4+texture*0.35)), 0, 100);
        String pigmentation = variance > 24 ? "High" : variance > 14 ? "Moderate" : "Low";

        int[] blemish = findBlemishes(bmp, skinRegions);

        List<PointF> allBrows = union(leftBrow, rightBrow);
        double browLuma = sampleLuminance(bmp, allBrows, box);
        String browShade = browLuma < 55 ? "Jet Black" : browLuma < 95 ? "Dark Brown" : "Soft Brown";
        double leftArch = archHeight(leftBrow); double rightArch = archHeight(rightBrow);
        double diff = Math.abs(leftArch-rightArch)/(Math.max(1.0, Math.max(leftArch,rightArch)));
        String browShape = diff < 0.18 ? "Symmetrical arch" : "Asymmetrical arch";

        LipShape lip = lipShape(upperLip, lowerLip);

        double[] density = mustacheDensity(bmp, box);
        String mustache;
        double diffPct = Math.abs(density[0]-density[1])/(Math.max(0.01, Math.max(density[0],density[1])));
        if (Math.max(density[0],density[1]) < 0.08) mustache = "Minimal / Clean";
        else if (diffPct > 0.30) mustache = density[0] > density[1] ? "Patchy/uneven on right" : "Patchy/uneven on left";
        else mustache = "Even growth";

        AnalysisReport r = new AnalysisReport();
        r.glow = glow; r.moisture = moisture; r.pigmentation = pigmentation;
        r.blemishCount = blemish[0]; r.blemishZone = blemish[1] == 1 ? "Cheeks / forehead" : "No significant zone";
        r.eyebrowShade = browShade; r.eyebrowShape = browShape;
        r.lipStructure = lip.name; r.mustacheGrowth = mustache;
        r.summary = String.format(Locale.US, "Glow %d%% | Moisture %d%% | %s pigmentation", glow, moisture, pigmentation);
        return r;
    }

    private AnalysisReport createFallbackReport() {
        AnalysisReport r = new AnalysisReport(); r.glow=0; r.moisture=0; r.pigmentation="Low";
        r.blemishCount=0; r.blemishZone="No face detected"; r.eyebrowShade="Not detected";
        r.eyebrowShape="Not detected"; r.lipStructure="Not detected"; r.mustacheGrowth="Not detected";
        r.summary="No clear face was detected. Use a front-facing, evenly lit photo."; return r;
    }

    private static List<PointF> points(Face face, FaceContour.Type type) {
        FaceContour c = face.getContour(type); return c == null ? new ArrayList<>() : c.getPoints();
    }
    private static List<PointF> union(List<PointF> a, List<PointF> b) { List<PointF> out=new ArrayList<>(a); out.addAll(b); return out; }

    private static Rect regionFromPoints(List<PointF> points, float shrink) {
        if (points.isEmpty()) return new Rect();
        float minX=points.get(0).x,minY=points.get(0).y,maxX=minX,maxY=minY;
        for(PointF p:points){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);} 
        int sx=(int)((maxX-minX)*shrink), sy=(int)((maxY-minY)*shrink);
        return new Rect((int)minX+sx,(int)minY+sy,(int)maxX-sx,(int)maxY-sy);
    }

    private static double luminance(int p) { return 0.299*((p>>16)&255)+0.587*((p>>8)&255)+0.114*(p&255); }
    private static double average(List<Double> v){double s=0;for(double d:v)s+=d;return v.isEmpty()?0:s/v.size();}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}

    private static double localContrast(Bitmap bmp, Rect r) {
        double sum=0; int n=0;
        for(int y=r.top+2;y<r.bottom-2;y+=5) for(int x=r.left+2;x<r.right-2;x+=5) {
            if(x+2<bmp.getWidth()&&y+2<bmp.getHeight()) {sum+=Math.abs(luminance(bmp.getPixel(x,y))-luminance(bmp.getPixel(x+3,y+2)));n++;}
        }
        return n==0?0:sum/n;
    }

    private int[] findBlemishes(Bitmap bmp, List<Rect> regions) {
        boolean[][] seen=new boolean[bmp.getWidth()][bmp.getHeight()]; int clusters=0;
        for(Rect r:regions) for(int y=r.top;y<r.bottom;y+=8) for(int x=r.left;x<r.right;x+=8) {
            if(x<0||y<0||x>=bmp.getWidth()||y>=bmp.getHeight()||seen[x][y]) continue;
            int p=bmp.getPixel(x,y), red=(p>>16)&255, green=(p>>8)&255;
            if(red-green>18) { clusters++; flood(bmp,seen,x,y,r); }
        }
        return new int[]{clusters, clusters>0?1:0};
    }
    private void flood(Bitmap bmp,boolean[][] seen,int sx,int sy,Rect r){List<PointF> stack=new ArrayList<>();stack.add(new PointF(sx,sy));
        while(!stack.isEmpty()){PointF pt=stack.remove(stack.size()-1);int x=(int)pt.x,y=(int)pt.y;if(seen[x][y])continue;seen[x][y]=true;
            if(x>r.left&&x<r.right&&y>r.top&&y<r.bottom&&x>0&&y>0&&x<bmp.getWidth()&&y<bmp.getHeight()){
                int p=bmp.getPixel(x,y); if(((p>>16)&255)-((p>>8)&255)>12){if(x>0&&!seen[x-1][y])stack.add(new PointF(x-1,y));if(y>0&&!seen[x][y-1])stack.add(new PointF(x,y-1));}}
        }
    }

    private double sampleLuminance(Bitmap bmp,List<PointF> pts,Rect box){double s=0;int n=0;for(PointF p:pts){int x=(int)p.x,y=(int)p.y;if(x>=0&&y>=0&&x<bmp.getWidth()&&y<bmp.getHeight()){s+=luminance(bmp.getPixel(x,y));n++;}}return n==0?120:s/n;}
    private double archHeight(List<PointF> pts){if(pts.isEmpty())return 0;double min=pts.get(0).y,max=min;for(PointF p:pts){min=Math.min(min,p.y);max=Math.max(max,p.y);}return max-min;}

    private LipShape lipShape(List<PointF> upper,List<PointF> lower){
        if(upper.isEmpty()&&lower.isEmpty())return new LipShape("Not detected");
        List<PointF> all=union(upper,lower);float minX=all.get(0).x,maxX=minX,minY=all.get(0).y,maxY=minY;
        for(PointF p:all){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);} 
        float width=maxX-minX,height=maxY-minY, ratio=height/Math.max(1,width);
        boolean symmetric=true; if(!upper.isEmpty()){
            float cy=(minY+maxY)/2;double left=0,right=0;for(PointF p:upper){if(p.x<(minX+maxX)/2)left+=Math.abs(p.y-cy);else right+=Math.abs(p.y-cy);}symmetric=Math.abs(left-right)/Math.max(1,left+right)<0.22;
        }
        String name=ratio<0.10f?"Thin":ratio>0.22f?"Full":"Balanced";if(symmetric)name+=", Symmetrical";
        return new LipShape(name);
    }

    private double[] mustacheDensity(Bitmap bmp,Rect box){
        int cx=(box.left+box.right)/2, top=(int)(box.top+box.height()*0.58), bottom=(int)(box.top+box.height()*0.78);double left=0,right=0;int ln=0,rn=0;
        top=Math.max(0,top);bottom=Math.min(bmp.getHeight(),bottom);
        for(int y=top;y<bottom;y+=3)for(int x=box.left;x<cx;x+=3){if(x>=0&&x<bmp.getWidth()){int p=bmp.getPixel(x,y);if(luminance(p)<105){left++;}ln++;}}
        for(int y=top;y<bottom;y+=3)for(int x=cx;x<box.right;x+=3){if(x>=0&&x<bmp.getWidth()){int p=bmp.getPixel(x,y);if(luminance(p)<105){right++;}rn++;}}
        return new double[]{ln==0?0:left/ln,rn==0?0:right/rn};
    }

    private static class LipShape { final String name; LipShape(String n){name=n;} }
}
