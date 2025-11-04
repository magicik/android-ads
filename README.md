# android-ads
<h2>I. Native Ads</h2>

**1. Thêm vào layout XML**

Chèn FrameLayout vào file layout của Activity/Fragment:

![img_1.png](img_1.png)

**2. Khởi tạo trong Activity / Fragment**

![img.png](img.png)

Giải thích ngắn:

- admobViewFactory / maxViewFactory: factory để trả về View hiển thị quảng cáo tương ứng.<br>

- AdmobViewFactory:<br>
  + Sử dụng template có sẵn là AdmobViewTemplate hoặc tạo view class kế thừa AdMobNativeViewBinder( thay cho AdobTemplateView)<br>   
  + Nếu sử dụng AdmobViewTemplate cần chú ý:<br>
      setTemplate(...): Có thể sử dụng 3 template có sẵn trong thư viện là mặc định `template_view_large_native_ads` nếu `null`, `template_view_medium_native_ads`, `template_view_small_native_ads`<br>
      hoặc có thể custom layout theo design từ module app rồi truyền vào<br>
- MaxViewFactory:<br>
  + Sử dụng MaxTemplateView <br>
  + Tạo class kế thừa MaxNativeViewBinder(thay cho MaxTemplateView)<br>

- loadInto(...): method để NativeAdManager tự thêm view quảng cáo vào container.<br>

**3. Xử lý onDestroy — giải phóng tài nguyên quảng cáo**
![img_2.png](img_2.png)

<h2>II. **Reward Ads**</h2>
 
Khởi tạo ở activity
  ![img_3.png](img_3.png)
Show reward
  ![img_4.png](img_4.png)
Giải phóng
    `native.onDestroy()`