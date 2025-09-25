package io.gnosis.safe.multichain.repositories

import io.gnosis.data.models.Chain
import io.gnosis.data.models.Safe
import io.gnosis.data.repositories.ChainInfoRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.multichain.models.MultichainSafe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger

class MultichainSafeRepositoryTest {

    @Mock
    private lateinit var safeRepository: SafeRepository
    
    @Mock
    private lateinit var chainInfoRepository: ChainInfoRepository
    
    @Mock
    private lateinit var preferencesManager: PreferencesManager
    
    private lateinit var multichainSafeRepository: MultichainSafeRepository

    private val testAddress = "0x1234567890123456789012345678901234567890".asEthereumAddress()!!
    private val testAddress2 = "0x0987654321098765432109876543210987654321".asEthereumAddress()!!

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        multichainSafeRepository = MultichainSafeRepository(
            safeRepository,
            chainInfoRepository,
            preferencesManager
        )
    }

    @Test
    fun `getMultichainSafes groups safes by address correctly`() = runTest {
        // Given
        val chain1 = createTestChain(BigInteger.ONE, "Ethereum")
        val chain2 = createTestChain(BigInteger.valueOf(137), "Polygon")
        
        val safe1Chain1 = createTestSafe(testAddress, "My Safe", BigInteger.ONE, chain1)
        val safe1Chain2 = createTestSafe(testAddress, "My Safe", BigInteger.valueOf(137), chain2)
        val safe2Chain1 = createTestSafe(testAddress2, "Other Safe", BigInteger.ONE, chain1)
        
        `when`(safeRepository.getSafes()).thenReturn(listOf(safe1Chain1, safe1Chain2, safe2Chain1))

        // When
        val multichainSafes = multichainSafeRepository.getMultichainSafes()

        // Then
        assertEquals(2, multichainSafes.size)
        
        val multichainSafe1 = multichainSafes.find { it.address == testAddress }
        assertNotNull(multichainSafe1)
        assertEquals("My Safe", multichainSafe1!!.localName)
        assertEquals(2, multichainSafe1.chainCount)
        assertTrue(multichainSafe1.isDeployedOnChain(BigInteger.ONE))
        assertTrue(multichainSafe1.isDeployedOnChain(BigInteger.valueOf(137)))
        
        val multichainSafe2 = multichainSafes.find { it.address == testAddress2 }
        assertNotNull(multichainSafe2)
        assertEquals("Other Safe", multichainSafe2!!.localName)
        assertEquals(1, multichainSafe2.chainCount)
        assertTrue(multichainSafe2.isDeployedOnChain(BigInteger.ONE))
        assertFalse(multichainSafe2.isDeployedOnChain(BigInteger.valueOf(137)))
    }

    @Test
    fun `getMultichainSafeByAddress returns correct safe`() = runTest {
        // Given
        val chain1 = createTestChain(BigInteger.ONE, "Ethereum")
        val safe = createTestSafe(testAddress, "My Safe", BigInteger.ONE, chain1)
        
        `when`(safeRepository.getSafes()).thenReturn(listOf(safe))

        // When
        val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(testAddress)

        // Then
        assertNotNull(multichainSafe)
        assertEquals(testAddress, multichainSafe!!.address)
        assertEquals("My Safe", multichainSafe.localName)
        assertEquals(1, multichainSafe.chainCount)
    }

    @Test
    fun `getMultichainSafeByAddress returns null for non-existent address`() = runTest {
        // Given
        `when`(safeRepository.getSafes()).thenReturn(emptyList())

        // When
        val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(testAddress)

        // Then
        assertNull(multichainSafe)
    }

    private fun createTestChain(chainId: BigInteger, name: String): Chain {
        return Chain(
            chainId = chainId,
            l2 = false,
            name = name,
            shortName = name.substring(0, minOf(3, name.length)).uppercase(),
            textColor = "#FFFFFF",
            backgroundColor = "#FF0000",
            rpcUri = "https://ethereum.rpc.url",
            rpcAuthentication = Chain.RpcAuthentication.NO_AUTHENTICATION,
            blockExplorerTemplateAddress = "https://etherscan.io/address/{{address}}",
            blockExplorerTemplateTxHash = "https://etherscan.io/tx/{{txHash}}",
            ensRegistryAddress = null,
            features = emptyList()
        ).apply {
            currency = Chain.Currency.DEFAULT_CURRENCY
        }
    }

    private fun createTestSafe(
        address: Solidity.Address,
        name: String,
        chainId: BigInteger,
        chain: Chain
    ): Safe {
        return Safe(
            address = address,
            localName = name,
            chainId = chainId
        ).apply {
            this.chain = chain
        }
    }
}
