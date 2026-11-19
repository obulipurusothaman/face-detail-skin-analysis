package com.example.faceapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.faceapp.databinding.ActivityResultBinding;

import java.io.File;
import java.io.FileOutputStream;

public class ResultActivity extends AppCompatActivity {
    private ActivityResultBinding binding;
    private Bitmap scannedBitmap;
    private AnalysisReport report;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding=ActivityResultBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());
        String path=getIntent().getStringExtra("image_path"); report=(AnalysisReport)getIntent().getSerializableExtra("report");
        if(path!=null){scannedBitmap=BitmapFactory.decodeFile(path);binding.resultImage.setImageBitmap(scannedBitmap);}
        if(report!=null)showReport(report);
        binding.exportButton.setOnClickListener(v->exportPdf());
    }

    private void showReport(AnalysisReport r){
        binding.glowText.setText("Glow: "+r.glow+"%");binding.moistureText.setText("Moisture: "+r.moisture+"%");
        binding.pigmentationText.setText("Pigmentation: "+r.pigmentation);binding.blemishText.setText("Blemishes: "+r.blemishCount+" — "+r.blemishZone);
        binding.eyebrowText.setText("Eyebrows: "+r.eyebrowShade+" — "+r.eyebrowShape);binding.lipText.setText("Lip structure: "+r.lipStructure);
        binding.mustacheText.setText("Mustache growth: "+r.mustacheGrowth);
    }

    private void exportPdf(){
        if(report==null){Toast.makeText(this,"No report available",Toast.LENGTH_SHORT).show();return;}
        PdfDocument document=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();
        PdfDocument.Page page=document.startPage(info);Canvas canvas=page.getCanvas();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);paint.setTextSize(18);canvas.drawText("Face Detail & Skin Analysis Report",36,48,paint);paint.setTextSize(10);
        int y=70;if(scannedBitmap!=null){int w=210,h=210*scannedBitmap.getHeight()/Math.max(1,scannedBitmap.getWidth());h=Math.min(h,260);canvas.drawBitmap(scaleImage(scannedBitmap,w,h),36,y,null);y+=h+18;} 
        paint.setTextSize(13);String[] lines={"Glow: "+report.glow+"%","Moisture: "+report.moisture+"%","Pigmentation: "+report.pigmentation,"Blemishes: "+report.blemishCount+" — "+report.blemishZone,"Eyebrows: "+report.eyebrowShade+" — "+report.eyebrowShape,"Lip structure: "+report.lipStructure,"Mustache growth: "+report.mustacheGrowth,"","Summary: "+report.summary};
        for(String line:lines){canvas.drawText(line,36,y,paint);y+=22;if(y>790){break;}}
        document.finishPage(page);
        try{File dir=new File(getCacheDir(),"reports");dir.mkdirs();File pdf=new File(dir,"face_analysis_report.pdf");try(FileOutputStream out=new FileOutputStream(pdf)){document.writeTo(out);}document.close();
            Uri uri=FileProvider.getUriForFile(this,"com.example.faceapp.fileprovider",pdf);Intent share=new Intent(Intent.ACTION_SEND);share.setType("application/pdf");share.putExtra(Intent.EXTRA_STREAM,uri);share.putExtra(Intent.EXTRA_SUBJECT,"Face Analysis Report");share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Share PDF report"));
        }catch(Exception e){document.close();Toast.makeText(this,"Could not export PDF",Toast.LENGTH_LONG).show();}
    }

    private Bitmap scaleImage(Bitmap source,int width,int height){return Bitmap.createScaledBitmap(source,width,height,true);}
}
