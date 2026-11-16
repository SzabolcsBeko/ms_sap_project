package com.techie.microservices.product;

import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MongoDBContainer;
import com.techie.microservices.product.repository.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductServiceApplicationTests {

	@ServiceConnection
	static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.5");
	@LocalServerPort
	private Integer port;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setup(){
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = port;
	}

	static{
		mongoDBContainer.start();
	}

	@Test
	void shouldCreateProduct() {
		String requestBody = """
				{
				    "name": "iPhone 15",
				    "description": "iPhone 15 is a smartphone from Apple",
				    "skuCode": "iphone_15",
				    "price": 1000
				}
				""";
		RestAssured.given()
				.contentType("application/json")
				.body(requestBody)
				.when()
				.post("/api/product")
				.then()
				.log().all()
				.statusCode(201)
				.body("id", Matchers.notNullValue())
				.body("name", Matchers.equalTo("iPhone 15"))
				.body("description", Matchers.equalTo("iPhone 15 is a smartphone from Apple"))
				.body("skuCode", Matchers.equalTo("iphone_15"))
				.body("price",Matchers.equalTo(1000));



	}

	@Test
	void shouldRejectAnEmptyProductAndSaveNothing() {
		long before = productRepository.count();

		RestAssured.given()
				.contentType("application/json")
				.body("{}")
				.when()
				.post("/api/product")
				.then()
				.log().all()
				.statusCode(400)
				.body("title", Matchers.equalTo("Invalid product"))
				.body("errors.skuCode", Matchers.equalTo("skuCode is required"))
				.body("errors.price", Matchers.equalTo("price is required"));

		assertThat(productRepository.count()).isEqualTo(before);
	}

}
